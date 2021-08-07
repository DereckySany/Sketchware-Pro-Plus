package dev.aldi.sayuti.editor.manage;

import static mod.SketchwareUtil.getDip;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.sketchware.remod.Resources;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import a.a.a.aB;
import a.a.a.xB;
import mod.SketchwareUtil;
import mod.agus.jcoderz.lib.FileUtil;
import mod.hey.studios.project.library.LibraryDownloader;
import mod.hey.studios.util.Helper;

public class ManageLocalLibraryActivity extends Activity implements View.OnClickListener, LibraryDownloader.OnCompleteListener {

    private static final String RESET_LOCAL_LIBRARIES_TAG = "reset_local_libraries";

    private final String local_libs_path = FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs/";
    private final ArrayList<HashMap<String, Object>> localLibraries = new ArrayList<>();
    private boolean configuringProject = false;
    private ListView listview;
    private String configurationFilePath = "";

    private void initToolbar() {
        ImageView back = findViewById(Resources.id.ig_toolbar_back);
        TextView title = findViewById(Resources.id.tx_toolbar_title);

        ImageView importLibrary = findViewById(Resources.id.ig_toolbar_load_file);

        Helper.applyRippleToToolbarView(back);
        back.setOnClickListener(Helper.getBackPressedClickListener(this));

        title.setText("Local library Manager");
        importLibrary.setPadding(
                (int) getDip(2),
                (int) getDip(2),
                (int) getDip(2),
                (int) getDip(2)
        );
        importLibrary.setImageResource(Resources.drawable.download_80px);
        importLibrary.setVisibility(View.VISIBLE);
        Helper.applyRippleToToolbarView(importLibrary);
        importLibrary.setOnClickListener(this);

        if (configuringProject) {
            ImageView reset = new ImageView(ManageLocalLibraryActivity.this);
            LinearLayout toolbar = (LinearLayout) back.getParent();
            toolbar.addView(reset, 2);

            reset.setTag(RESET_LOCAL_LIBRARIES_TAG);
            {
                ViewGroup.LayoutParams layoutParams = importLibrary.getLayoutParams();
                if (layoutParams != null) {
                    reset.setLayoutParams(layoutParams);
                }
            }
            reset.setImageResource(Resources.drawable.ic_restore_white_24dp);
            reset.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            Helper.applyRippleToToolbarView(reset);
            reset.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == Resources.id.ig_toolbar_load_file) {
            new AlertDialog.Builder(this)
                    .setTitle("Dexer")
                    .setMessage("Would you like to use Dx or D8 to dex the library?\n" +
                            "D8 supports Java 8, whereas Dx does not. Limitation: D8 only works on Android 8 and above.")
                    .setPositiveButton("D8", (dialogInterface, i) ->
                            new LibraryDownloader(ManageLocalLibraryActivity.this, true)
                                    .showDialog(ManageLocalLibraryActivity.this))
                    .setNegativeButton("Dx", (dialogInterface, i) ->
                            new LibraryDownloader(ManageLocalLibraryActivity.this, false)
                                    .showDialog(ManageLocalLibraryActivity.this))
                    .setNeutralButton(Resources.string.common_word_cancel, null)
                    .show();
        } else if (RESET_LOCAL_LIBRARIES_TAG.equals(v.getTag())) {
            if (configuringProject) {
                aB dialog = new aB(this);
                dialog.a(Resources.drawable.rollback_96);
                dialog.b("Reset libraries?");
                dialog.a("This will reset all used local libraries for this project. Are you sure?");
                dialog.a(xB.b().a(getApplicationContext(), Resources.string.common_word_cancel),
                        Helper.getDialogDismissListener(dialog));
                dialog.b(xB.b().a(getApplicationContext(), Resources.string.common_word_reset), view -> {
                    FileUtil.writeFile(configurationFilePath, "[]");
                    SketchwareUtil.toast("Successfully reset local libraries");
                    loadFiles();
                    dialog.dismiss();
                });
                dialog.show();
            }
        }
    }

    @Override
    public void onComplete() {
        loadFiles();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(Resources.layout.manage_permission);

        SwipeRefreshLayout refreshLayout = new SwipeRefreshLayout(this);
        LinearLayout searchViewContainer = findViewById(Resources.id.managepermissionLinearLayout1);
        searchViewContainer.setVisibility(View.GONE);
        listview = findViewById(Resources.id.main_content);

        ViewGroup mainContent = (ViewGroup) searchViewContainer.getParent();
        ViewGroup root = (ViewGroup) mainContent.getParent();
        root.removeView(mainContent);
        refreshLayout.addView(mainContent);
        root.addView(refreshLayout);

        refreshLayout.setOnRefreshListener(() -> {
            saveConfiguration();
            loadFiles();
            new Handler(Looper.myLooper()).postDelayed(() -> refreshLayout.setRefreshing(false),
                    500);
        });
        listview.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                int topRowVerticalPosition = (listview.getChildCount() == 0) ?
                        0 : listview.getChildAt(0).getTop();
                refreshLayout.setEnabled(topRowVerticalPosition >= 0);
            }
        });

        if (getIntent().hasExtra("sc_id")) {
            String sc_id = getIntent().getStringExtra("sc_id");
            configurationFilePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/local_library";
            configuringProject = !sc_id.equals("system");
            initToolbar();
            loadFiles();
        } else {
            finish();
        }
    }

    @Override
    protected void onStop() {
        saveConfiguration();
        super.onStop();
    }

    private void loadFiles() {
        ArrayList<HashMap<String, Object>> inUseLocalLibraries = new ArrayList<>();
        localLibraries.clear();
        if (configuringProject) {
            if (!FileUtil.isExistFile(configurationFilePath) || FileUtil.readFile(configurationFilePath).equals("")) {
                FileUtil.writeFile(configurationFilePath, "[]");
            } else {
                try {
                    inUseLocalLibraries = new Gson().fromJson(FileUtil.readFile(configurationFilePath), Helper.TYPE_MAP_LIST);
                } catch (JsonParseException e) {
                    SketchwareUtil.toastError("Failed to parse used Local libraries file: " + e.getMessage());
                }
            }
        }

        ArrayList<String> files = new ArrayList<>();
        FileUtil.listDir(local_libs_path, files);
        Collections.sort(files, String.CASE_INSENSITIVE_ORDER);

        for (String file : files) {
            if (FileUtil.isDirectory(file)) {
                HashMap<String, Object> map = new HashMap<>();
                map.put("name", Uri.parse(file).getLastPathSegment());
                localLibraries.add(map);
            }
        }

        for (int i = 0, localLibrariesSize = localLibraries.size(); i < localLibrariesSize; i++) {
            HashMap<String, Object> availableLocalLibrary = localLibraries.get(i);
            Object availableLocalLibraryName = availableLocalLibrary.get("name");

            if (availableLocalLibraryName instanceof String) {
                if (inUseLocalLibraries.size() == 0) {
                    availableLocalLibrary.put("inUse", false);
                } else {
                    for (HashMap<String, Object> inUseLibrary : inUseLocalLibraries) {
                        Object inUseLibraryName = inUseLibrary.get("name");

                        if (inUseLibraryName instanceof String) {
                            if (availableLocalLibraryName.equals(inUseLibraryName)) {
                                availableLocalLibrary.put("inUse", true);
                                inUseLocalLibraries.remove(inUseLibrary);
                                break;
                            } else {
                                availableLocalLibrary.put("inUse", false);
                            }
                        }
                    }
                }
            } else {
                SketchwareUtil.toastError("In-use Local library #" + (i + 1) + " has an invalid name!");
            }
        }

        listview.setAdapter(new LibraryAdapter(localLibraries));
        ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
    }

    private void saveConfiguration() {
        ArrayList<HashMap<String, Object>> inUseLibraries = new ArrayList<>();

        for (HashMap<String, Object> localLibrary : localLibraries) {
            Object inUse = localLibrary.get("inUse");

            if (inUse instanceof Boolean && (Boolean) inUse) {
                Object localLibraryName = localLibrary.get("name");

                if (localLibraryName instanceof String) {
                    HashMap<String, Object> libraryMetadata = new HashMap<>();
                    libraryMetadata.put("name", localLibraryName);

                    String libraryPath = local_libs_path + (String) localLibraryName;
                    List<File> libraryPathChildren = new ArrayList<>();
                    {
                        File[] files = new File(libraryPath).listFiles();

                        if (files != null) {
                            libraryPathChildren = Arrays.asList(files);
                        }
                    }

                    if (libraryPathChildren.contains(new File(libraryPath + "/config"))) {
                        libraryMetadata.put("packageName", FileUtil.readFile(libraryPath + "/config"));
                    }
                    if (libraryPathChildren.contains(new File(libraryPath + "/res"))) {
                        libraryMetadata.put("resPath", libraryPath + "/res");
                    }
                    if (libraryPathChildren.contains(new File(libraryPath + "/classes.jar"))) {
                        libraryMetadata.put("jarPath", libraryPath + "/classes.jar");
                    }
                    if (libraryPathChildren.contains(new File(libraryPath + "/classes.dex"))) {
                        libraryMetadata.put("dexPath", libraryPath.concat("/classes.dex"));
                    }
                    if (libraryPathChildren.contains(new File(libraryPath.concat("/AndroidManifest.xml")))) {
                        libraryMetadata.put("manifestPath", libraryPath.concat("/AndroidManifest.xml"));
                    }
                    if (libraryPathChildren.contains(new File(libraryPath.concat("/proguard.txt")))) {
                        libraryMetadata.put("pgRulesPath", libraryPath.concat("/proguard.txt"));
                    }
                    if (libraryPathChildren.contains(new File(libraryPath.concat("/assets")))) {
                        libraryMetadata.put("assetsPath", libraryPath.concat("/assets"));
                    }

                    inUseLibraries.add(libraryMetadata);
                }
            }
        }

        FileUtil.writeFile(configurationFilePath, new Gson().toJson(inUseLibraries));
    }

    private class LibraryAdapter extends BaseAdapter {

        private final ArrayList<HashMap<String, Object>> _data;

        public LibraryAdapter(ArrayList<HashMap<String, Object>> arrayList) {
            _data = arrayList;
        }

        @Override
        public HashMap<String, Object> getItem(int position) {
            return _data.get(position);
        }

        @Override
        public int getCount() {
            return _data.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(Resources.layout.view_item_local_lib, null);
            }
            final CheckBox checkBox = convertView.findViewById(Resources.id.checkbox_content);
            final ImageView options = convertView.findViewById(Resources.id.img_delete);

            final HashMap<String, Object> currentLibrary = _data.get(position);

            Object name = currentLibrary.get("name");
            if (name instanceof String) {
                checkBox.setText((String) name);
            }

            if (configuringProject) {
                Object currentLibraryInUse = currentLibrary.get("inUse");

                if (currentLibraryInUse instanceof Boolean) {
                    checkBox.setChecked((Boolean) currentLibraryInUse);
                } else {
                    currentLibrary.remove("inUse");
                    checkBox.setChecked(false);
                }
            } else {
                checkBox.setEnabled(false);
            }

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Log.d("ManageLocalLibraryActivity", "OnCheckedChange for position " + position + ", isChecked: " + isChecked);
                Log.d("ManageLocalLibraryActivity", "localLibraries as String: " + localLibraries);
                _data.get(position).put("inUse", isChecked);
            });

            options.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(ManageLocalLibraryActivity.this, v);

                Menu menu = popupMenu.getMenu();
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Rename");
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Delete");

                popupMenu.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getTitle().toString()) {
                        case "Delete":
                            checkBox.setChecked(false);
                            FileUtil.deleteFile(local_libs_path + checkBox.getText().toString());
                            loadFiles();
                            break;

                        case "Rename":
                            final AlertDialog dialog = new AlertDialog.Builder(ManageLocalLibraryActivity.this).create();

                            final View root = getLayoutInflater().inflate(Resources.layout.dialog_input_layout, null);
                            final LinearLayout title = root.findViewById(Resources.id.dialoginputlayoutLinearLayout1);
                            final TextInputLayout tilFilename = root.findViewById(Resources.id.dialoginputlayoutLinearLayout2);
                            final EditText filename = root.findViewById(Resources.id.edittext_change_name);

                            final View titleChildAt1 = title.getChildAt(1);
                            if (titleChildAt1 instanceof TextView) {
                                final TextView titleTextView = (TextView) titleChildAt1;
                                titleTextView.setText("Rename local library");
                            }

                            tilFilename.setHint("New local library name");
                            filename.setText(checkBox.getText().toString());
                            root.findViewById(Resources.id.text_cancel)
                                    .setOnClickListener(Helper.getDialogDismissListener(dialog));
                            root.findViewById(Resources.id.text_save)
                                    .setOnClickListener(view -> {
                                        checkBox.setChecked(false);
                                        File input = new File(local_libs_path.concat(checkBox.getText().toString()));
                                        File output = new File(local_libs_path.concat(filename.getText().toString()));
                                        if (!input.renameTo(output)) {
                                            SketchwareUtil.toastError("Failed to rename library");
                                        }
                                        SketchwareUtil.toast("NOTE: Removed library from used local libraries");
                                        dialog.dismiss();
                                    });
                            dialog.setView(root);
                            dialog.show();

                            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                            filename.requestFocus();
                            break;

                        default:
                            return false;
                    }

                    return true;
                });
                popupMenu.show();
            });

            return convertView;
        }
    }
}
