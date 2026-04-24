package org.autojs.autojs.ui.edit;

import static org.autojs.autojs.model.script.Scripts.ACTION_ON_EXECUTION_FINISHED;
import static org.autojs.autojs.model.script.Scripts.EXTRA_EXCEPTION_COLUMN_NUMBER;
import static org.autojs.autojs.model.script.Scripts.EXTRA_EXCEPTION_LINE_NUMBER;
import static org.autojs.autojs.model.script.Scripts.EXTRA_EXCEPTION_MESSAGE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.snackbar.Snackbar;
import com.stardust.autojs.engine.JavaScriptEngine;
import com.stardust.autojs.engine.ScriptEngine;
import com.stardust.autojs.execution.ScriptExecution;
import com.stardust.pio.PFiles;
import com.stardust.util.BackPressedHandler;
import com.stardust.util.Callback;
import com.stardust.util.ViewUtils;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EViewGroup;
import org.androidannotations.annotations.ViewById;
import org.autojs.autojs.Pref;
import org.autojs.autojs.ui.main.web.DocumentSource;
import org.autojs.autojs.ui.main.web.EditorAppManager;
import org.autojs.autoxjs.R;
import org.autojs.autojs.autojs.AutoJs;
import org.autojs.autojs.model.autocomplete.AutoCompletion;
import org.autojs.autojs.model.autocomplete.CodeCompletion;
import org.autojs.autojs.model.autocomplete.CodeCompletions;
import org.autojs.autojs.model.autocomplete.Symbols;
import org.autojs.autojs.model.indices.Module;
import org.autojs.autojs.model.indices.Property;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.tool.Observers;
import org.autojs.autojs.ui.doc.ManualDialog;
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar;
import org.autojs.autojs.ui.edit.debug.DebugBar;
import org.autojs.autojs.ui.edit.editor.CodeEditor;
import org.autojs.autojs.ui.edit.keyboard.FunctionsKeyboardHelper;
import org.autojs.autojs.ui.edit.keyboard.FunctionsKeyboardView;
import org.autojs.autojs.ui.edit.theme.Theme;
import org.autojs.autojs.ui.edit.theme.Themes;
import org.autojs.autojs.ui.edit.toolbar.DebugToolbarFragment;
import org.autojs.autojs.ui.edit.toolbar.DebugToolbarFragment_;
import org.autojs.autojs.ui.edit.toolbar.NormalToolbarFragment;
import org.autojs.autojs.ui.edit.toolbar.NormalToolbarFragment_;
import org.autojs.autojs.ui.edit.toolbar.SearchToolbarFragment;
import org.autojs.autojs.ui.edit.toolbar.SearchToolbarFragment_;
import org.autojs.autojs.ui.edit.toolbar.ToolbarFragment;
import org.autojs.autojs.ui.log.LogActivityKt;
import org.autojs.autojs.ui.widget.EWebView;
import org.autojs.autojs.ui.widget.SimpleTextWatcher;

import java.io.File;
import java.util.List;
import java.util.Objects;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
/**
 * Created by Stardust on 2017/9/28.
 */
@SuppressLint("NonConstantResourceId")
@EViewGroup(R.layout.editor_view)
public class EditorView extends FrameLayout implements CodeCompletionBar.OnHintClickListener, FunctionsKeyboardView.ClickCallback, ToolbarFragment.OnMenuItemClickListener {

    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_CONTENT = "content";
    public static final String EXTRA_READ_ONLY = "readOnly";
    public static final String EXTRA_SAVE_ENABLED = "saveEnabled";
    public static final String EXTRA_RUN_ENABLED = "runEnabled";

    @ViewById(R.id.editor)
    CodeEditor mEditor;

    @ViewById(R.id.code_completion_bar)
    CodeCompletionBar mCodeCompletionBar;

    @ViewById(R.id.input_method_enhance_bar)
    View mInputMethodEnhanceBar;

    @ViewById(R.id.symbol_bar)
    CodeCompletionBar mSymbolBar;

    @ViewById(R.id.functions)
    ImageView mShowFunctionsButton;

    @ViewById(R.id.functions_keyboard)
    FunctionsKeyboardView mFunctionsKeyboard;

    @ViewById(R.id.debug_bar)
    DebugBar mDebugBar;

    @ViewById(R.id.docs)
    EWebView mDocsWebView;

    @ViewById(R.id.drawer_layout)
    DrawerLayout mDrawerLayout;

    @ViewById(R.id.toggle_change)
    ImageView mToggleChange;

    @ViewById(R.id.first_row_buttons)
    LinearLayout mFirstRowButtons;

    @ViewById(R.id.second_row_buttons)
    LinearLayout mSecondRowButtons;

    @ViewById(R.id.toggled_select)
    ImageView mToggledSelect;

    @ViewById(R.id.toggled_up)
    ImageView mToggledUp;

    @ViewById(R.id.toggled_copy)
    ImageView mToggledCopy;

    @ViewById(R.id.toggled_space)
    ImageView mToggledSpace;

    @ViewById(R.id.toggled_left)
    ImageView mToggledLeft;

    @ViewById(R.id.toggled_down)
    ImageView mToggledDown;

    @ViewById(R.id.toggled_right)
    ImageView mToggledRight;

    private int mSelectionLevel = 0;
    private String mName;
    private Uri mUri;
    private boolean mReadOnly = false;
    private int mScriptExecutionId;
    private AutoCompletion mAutoCompletion;
    private Theme mEditorTheme;
    private FunctionsKeyboardHelper mFunctionsKeyboardHelper;

    private BroadcastReceiver mOnRunFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_ON_EXECUTION_FINISHED.equals(intent.getAction())) {
                mScriptExecutionId = ScriptExecution.NO_ID;
                if (mDebugging) {
                    exitDebugging();
                }
                setMenuItemStatus(R.id.run, true);
                String msg = intent.getStringExtra(EXTRA_EXCEPTION_MESSAGE);
                int line = intent.getIntExtra(EXTRA_EXCEPTION_LINE_NUMBER, -1);
                int col = intent.getIntExtra(EXTRA_EXCEPTION_COLUMN_NUMBER, 0);
                if (line >= 1) {
                    mEditor.jumpTo(line - 1, col);
                }
                if (msg != null) {
                    showErrorMessage(msg);
                }
            }
        }
    };
    private SparseBooleanArray mMenuItemStatus = new SparseBooleanArray();
    private String mRestoredText;
    private NormalToolbarFragment mNormalToolbar = new NormalToolbarFragment_();
    private boolean mDebugging = false;
    private EditorMenu mEditorMenu;

    public EditorView(Context context) {
        super(context);
    }

    public EditorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public EditorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().registerReceiver(mOnRunFinishedReceiver, new IntentFilter(ACTION_ON_EXECUTION_FINISHED));
        if (getContext() instanceof BackPressedHandler.HostActivity) {
            ((BackPressedHandler.HostActivity) getContext()).getBackPressedObserver().registerHandler(mFunctionsKeyboardHelper);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(mOnRunFinishedReceiver);
        if (getContext() instanceof BackPressedHandler.HostActivity) {
            ((BackPressedHandler.HostActivity) getContext()).getBackPressedObserver().unregisterHandler(mFunctionsKeyboardHelper);
        }
    }

    public Uri getUri() {
        return mUri;
    }

    public Observable<String> handleIntent(Intent intent) {
        mName = intent.getStringExtra(EXTRA_NAME);
        return handleText(intent)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(str -> {
                    mReadOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false);
                    boolean saveEnabled = intent.getBooleanExtra(EXTRA_SAVE_ENABLED, true);
                    if (mReadOnly || !saveEnabled) {
                        findViewById(R.id.save).setVisibility(View.GONE);
                    }
                    if (!intent.getBooleanExtra(EXTRA_RUN_ENABLED, true)) {
                        findViewById(R.id.run).setVisibility(GONE);
                    }
                    if (mReadOnly) {
                        mEditor.setReadOnly(true);
                    }
                });
    }

    public void setRestoredText(String text) {
        mRestoredText = text;
        mEditor.setText(text);
    }

    private Observable<String> handleText(Intent intent) {
        String path = intent.getStringExtra(EXTRA_PATH);
        String content = intent.getStringExtra(EXTRA_CONTENT);
        if (content != null) {
            setInitialText(content);
            return Observable.just(content);
        } else {
            if (path == null) {
                if (intent.getData() == null) {
                    return Observable.error(new IllegalArgumentException("path and content is empty"));
                } else {
                    mUri = intent.getData();
                }
            } else {
                mUri = Uri.fromFile(new File(path));
            }
            if (mName == null) {
                mName = PFiles.getNameWithoutExtension(mUri.getPath());
            }
            return loadUri(mUri);
        }
    }

    @SuppressLint("CheckResult")
    private Observable<String> loadUri(final Uri uri) {
        mEditor.setProgress(true);
        return Observable.fromCallable(() -> PFiles.read(getContext().getContentResolver().openInputStream(uri)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(s -> {
                    setInitialText(s);
                    mEditor.setProgress(false);
                });
    }

    private void setInitialText(String text) {
        if (mRestoredText != null) {
            mEditor.setText(mRestoredText);
            mRestoredText = null;
            return;
        }
        mEditor.setInitialText(text);
    }

    private void setMenuItemStatus(int id, boolean enabled) {
        mMenuItemStatus.put(id, enabled);
        ToolbarFragment fragment = (ToolbarFragment) getActivity().getSupportFragmentManager()
                .findFragmentById(R.id.toolbar_menu);
        if (fragment == null) {
            mNormalToolbar.setMenuItemStatus(id, enabled);
        } else {
            fragment.setMenuItemStatus(id, enabled);
        }
    }

    public boolean getMenuItemStatus(int id, boolean defValue) {
        return mMenuItemStatus.get(id, defValue);
    }

    @AfterViews
    void init() {
        //setTheme(Theme.getDefault(getContext()));
        setUpEditor();
        setUpInputMethodEnhancedBar();
        setUpFunctionsKeyboard();
        setMenuItemStatus(R.id.save, false);
        setToggle_change(); // 设置切换按钮点击监听


       /* if (mDocsWebView.getIsTbs()) {
            mDocsWebView.getWebViewTbs().getSettings().setDisplayZoomControls(true);
            mDocsWebView.getWebViewTbs().loadUrl(Pref.getDocumentationUrl() + "index.html");
        } else {
            mDocsWebView.getWebView().getSettings().setDisplayZoomControls(true);
            mDocsWebView.getWebView().loadUrl(Pref.getDocumentationUrl() + "index.html");
        }*/
        // 核心：读取保存的文档源，自动加载对应文档
        // 核心：读取保存的文档源，自动加载对应文档


        // 核心：读取保存的文档源，自动加载对应文档
        SharedPreferences sp = EditorAppManager.Companion.getSaveStatus(getContext());
        String name = sp.getString(EditorAppManager.DocumentSourceKEY, DocumentSource.DOC_V1.name());
        DocumentSource source;
        try {
            source = DocumentSource.valueOf(name);
        } catch (IllegalArgumentException e) {
            source = DocumentSource.DOC_V1; // 读取失败默认本地文档1
        }

        // 复用 switchDocument 逻辑，自动加载（统一用原生WebView，TBS自动兼容）
        EditorAppManager.Companion.switchDocument(mDocsWebView.getWebView(), source);


        Themes.getCurrent(getContext())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setTheme);
        initNormalToolbar();
    }

    private void initNormalToolbar() {
        mNormalToolbar.setOnMenuItemClickListener(this);
        mNormalToolbar.setOnMenuItemLongClickListener(view -> {
            if (view.getId() == R.id.run) {
                debug();
                return true;
            }
            return false;
        });
        Fragment fragment = getActivity().getSupportFragmentManager().findFragmentById(R.id.toolbar_menu);
        if (fragment == null) {
            showNormalToolbar();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setUpFunctionsKeyboard() {
        mFunctionsKeyboardHelper = FunctionsKeyboardHelper.with((Activity) getContext())
                .setContent(mEditor)
                .setFunctionsTrigger(mShowFunctionsButton)
                .setFunctionsView(mFunctionsKeyboard)
                .setEditView(mEditor.getCodeEditText())
                .build();
        mFunctionsKeyboard.setClickCallback(this);
        // =============================================
        // 👇 只加这一段！只提示！不影响任何功能！
        // =============================================
        mShowFunctionsButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {

                // ========== 延迟 200 毫秒 再判断位置 ==========
                v.postDelayed(() -> {
                    int[] location = new int[2];
                    mShowFunctionsButton.getLocationOnScreen(location);
                    int buttonY = location[1];
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;

                    boolean isButtonAtBottom = mShowFunctionsButton.getVisibility() == View.VISIBLE
                            && buttonY > screenHeight * 0.8f;

                    if (isButtonAtBottom) {
                        // 判断软键盘是否弹出
                        boolean isKeyboardShown = isSoftInputShown();
                        if (isKeyboardShown) {
                            // 弹出键盘: 显示提示2
                            Toast.makeText(getContext(), "打开软键盘，不显示函数面板", Toast.LENGTH_SHORT).show();
                        } else {
                            // 未弹出键盘: 显示提示1
                            Toast.makeText(getContext(), "关闭函数面板一次", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, 200);

            }
            return false;
        });
    }
    /**
     * 判断软键盘是否弹出
     */
    private boolean isSoftInputShown() {
        final int SOFT_INPUT_THRESHOLD = 100;
        View rootView = mShowFunctionsButton.getRootView();
        Rect rect = new Rect();
        rootView.getWindowVisibleDisplayFrame(rect);
        int screenHeight = rootView.getResources().getDisplayMetrics().heightPixels;
        int keyboardHeight = screenHeight - rect.bottom;
        return keyboardHeight > SOFT_INPUT_THRESHOLD;
    }
    private void setUpInputMethodEnhancedBar() {
        mSymbolBar.setCodeCompletions(Symbols.getSymbols());
        mCodeCompletionBar.setOnHintClickListener(this);
        mSymbolBar.setOnHintClickListener(this);
        mAutoCompletion = new AutoCompletion(getContext(), mEditor.getCodeEditText());
        mAutoCompletion.setAutoCompleteCallback(mCodeCompletionBar::setCodeCompletions);
    }

    private void setUpEditor() {
        mEditor.getCodeEditText().addTextChangedListener(new SimpleTextWatcher(s -> {
            setMenuItemStatus(R.id.save, mEditor.isTextChanged());
            setMenuItemStatus(R.id.undo, mEditor.canUndo());
            setMenuItemStatus(R.id.redo, mEditor.canRedo());
        }));
        mEditor.addCursorChangeCallback(this::autoComplete);
        mEditor.getCodeEditText().setTextSize(Pref.getEditorTextSize((int) ViewUtils.pxToSp(getContext(), mEditor.getCodeEditText().getTextSize())));
        mEditorMenu = new EditorMenu(this);
    }

    private void autoComplete(String line, int cursor) {
        mAutoCompletion.onCursorChange(line, cursor);
    }

    public DebugBar getDebugBar() {
        return mDebugBar;
    }

    public void setTheme(Theme theme) {
        mEditorTheme = theme;
        mEditor.setTheme(theme);
        mInputMethodEnhanceBar.setBackgroundColor(theme.getImeBarBackgroundColor());
        int textColor = theme.getImeBarForegroundColor();
        mCodeCompletionBar.setTextColor(textColor);
        mSymbolBar.setTextColor(textColor);
        mShowFunctionsButton.setColorFilter(textColor);
        invalidate();
    }

    public boolean onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            if (mDocsWebView.getIsTbs()) {
                if (mDocsWebView.getWebViewTbs().canGoBack()) {
                    mDocsWebView.getWebViewTbs().goBack();
                } else {
                    mDrawerLayout.closeDrawer(GravityCompat.START);
                }
            } else {
                if (mDocsWebView.getWebView().canGoBack()) {
                    mDocsWebView.getWebView().goBack();
                } else {
                    mDrawerLayout.closeDrawer(GravityCompat.START);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onToolbarMenuItemClick(View view) {
        switch (view.getId()) {
            case R.id.run:
                runAndSaveFileIfNeeded();
                break;
            case R.id.save:
                saveFile();
                break;
            case R.id.undo:
                undo();
                break;
            case R.id.redo:
                redo();
                break;
            case R.id.replace:
                replace();
                break;
            case R.id.find_next:
                findNext();
                break;
            case R.id.find_prev:
                findPrev();
                break;
            case R.id.cancel_search:
                cancelSearch();
                break;
            case R.id.action_log:
                LogActivityKt.start(getContext());
                break;
            case R.id.debug:
                showOptionMenu(view, R.menu.menu_editor_debug);
                break;
            case R.id.jump:
                showOptionMenu(view, R.menu.menu_editor_jump);
                break;
            case R.id.edit:
                showOptionMenu(view, R.menu.menu_editor_edit);
                break;
            case R.id.others:
                showOptionMenu(view, R.menu.menu_editor);
                break;
        }
    }

    void showOptionMenu(View view, int menuId) {
        PopupMenu popupMenu = new PopupMenu(getContext(), view);
        popupMenu.inflate(menuId);
        popupMenu.setOnMenuItemClickListener(mEditorMenu::onOptionsItemSelected);
        popupMenu.show();
    }

    @SuppressLint("CheckResult")
    public void runAndSaveFileIfNeeded() {
        save().observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> run(true), Observers.toastMessage());
    }

    public ScriptExecution run(boolean showMessage) {
        if (showMessage) {
            Snackbar.make(this, R.string.text_start_running, Snackbar.LENGTH_SHORT).show();
        }
        // TODO: 2018/10/24
        ScriptExecution execution = Scripts.INSTANCE.runWithBroadcastSender(new File(mUri.getPath()));
        if (execution == null) {
            return null;
        }
        mScriptExecutionId = execution.getId();
        setMenuItemStatus(R.id.run, false);
        return execution;
    }

    public void undo() {
        mEditor.undo();
    }

    public void redo() {
        mEditor.redo();
    }

    public Observable<String> save() {
        String path = mUri.getPath();
        String backPath = path + ".b_a_k";
        PFiles.move(path, backPath);
        return Observable.just(mEditor.getText())
                .observeOn(Schedulers.io())
                .doOnNext(s -> PFiles.write(getContext().getContentResolver().openOutputStream(mUri), s))
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(s -> {
                    mEditor.markTextAsSaved();
                    setMenuItemStatus(R.id.save, false);
                })
                .doOnNext(s -> new File(backPath).delete());
    }

    public void forceStop() {
        doWithCurrentEngine(ScriptEngine::forceStop);
    }

    private void doWithCurrentEngine(Callback<ScriptEngine> callback) {
        ScriptExecution execution = AutoJs.getInstance().getScriptEngineService().getScriptExecution(mScriptExecutionId);
        if (execution != null) {
            ScriptEngine engine = execution.getEngine();
            if (engine != null) {
                callback.call(engine);
            }
        }
    }

    @SuppressLint("CheckResult")
    public void saveFile() {
        // hideKeyboardInput();
        save()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Observers.emptyConsumer(), e -> {
                    e.printStackTrace();
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    void findNext() {
        mEditor.findNext();
    }

    void findPrev() {
        mEditor.findPrev();
    }

    void cancelSearch() {
        showNormalToolbar();
    }

    private void showNormalToolbar() {
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.toolbar_menu, mNormalToolbar)
                .commitAllowingStateLoss();
    }

    FragmentActivity getActivity() {
        Context context = getContext();
        while (!(context instanceof Activity) && context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return (FragmentActivity) context;
    }

    void replace() {
        mEditor.replaceSelection();
    }

    public String getName() {
        return mName;
    }

    public boolean isTextChanged() {
        return mEditor.isTextChanged();
    }

    public void showConsole() {
        doWithCurrentEngine(engine -> ((JavaScriptEngine) engine).getRuntime().console.show());
    }

    public void openByOtherApps() {
        if (mUri != null) {
            Scripts.INSTANCE.openByOtherApps(mUri);
        }
    }

    public void beautifyCode() {
        mEditor.beautifyCode();
    }

    public void selectEditorTheme() {
        mEditor.setProgress(true);
        Themes.getAllThemes(getContext())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(themes -> {
                    mEditor.setProgress(false);
                    selectEditorTheme(themes);
                });
    }

    public void selectTextSize() {
        new TextSizeSettingDialogBuilder(getContext())
                .initialValue((int) ViewUtils.pxToSp(getContext(), mEditor.getCodeEditText().getTextSize()))
                .callback(this::setTextSize)
                .show();
    }

    public void setTextSize(int value) {
        Pref.setEditorTextSize(value);
        mEditor.getCodeEditText().setTextSize(value);
    }

    public void setTextSizePlus() {
        int value = (int) ViewUtils.pxToSp(getContext(), mEditor.getCodeEditText().getTextSize());
        Pref.setEditorTextSize(Math.min(value + 2, 60));
        mEditor.getCodeEditText().setTextSize(Math.min(value + 2, 60));
    }

    public void setTextSizeMinus() {
        int value = (int) ViewUtils.pxToSp(getContext(), mEditor.getCodeEditText().getTextSize());
        Pref.setEditorTextSize(Math.max(value - 2, 2));
        mEditor.getCodeEditText().setTextSize(Math.max(value - 2, 2));
    }

    private void selectEditorTheme(List<Theme> themes) {
        int i = themes.indexOf(mEditorTheme);
        if (i < 0) {
            i = 0;
        }
        new MaterialDialog.Builder(getContext())
                .title(R.string.text_editor_theme)
                .items(themes)
                .itemsCallbackSingleChoice(i, (dialog, itemView, which, text) -> {
                    setTheme(themes.get(which));
                    Themes.setCurrent(themes.get(which).getName());
                    return true;
                })
                .show();
    }

    public CodeEditor getEditor() {
        return mEditor;
    }

    public void find(String keywords, boolean usingRegex) throws CodeEditor.CheckedPatternSyntaxException {
        mEditor.find(keywords, usingRegex);
        showSearchToolbar(false);
    }

    private void showSearchToolbar(boolean showReplaceItem) {
        SearchToolbarFragment searchToolbarFragment = SearchToolbarFragment_.builder()
                .arg(SearchToolbarFragment.ARGUMENT_SHOW_REPLACE_ITEM, showReplaceItem)
                .build();
        searchToolbarFragment.setOnMenuItemClickListener(this);
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.toolbar_menu, searchToolbarFragment)
                .commit();
    }

    public void replace(String keywords, String replacement, boolean usingRegex) throws CodeEditor.CheckedPatternSyntaxException {
        mEditor.replace(keywords, replacement, usingRegex);
        showSearchToolbar(true);
    }

    public void replaceAll(String keywords, String replacement, boolean usingRegex) throws CodeEditor.CheckedPatternSyntaxException {
        mEditor.replaceAll(keywords, replacement, usingRegex);
    }

    public void debug() {
        DebugToolbarFragment debugToolbarFragment = DebugToolbarFragment_.builder()
                .build();
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.toolbar_menu, debugToolbarFragment)
                .commit();
        mDebugBar.setVisibility(VISIBLE);
        mInputMethodEnhanceBar.setVisibility(GONE);
        mDebugging = true;
    }

    public void exitDebugging() {
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.toolbar_menu);
        if (fragment instanceof DebugToolbarFragment) {
            ((DebugToolbarFragment) fragment).detachDebugger();
        }
        showNormalToolbar();
        mEditor.setDebuggingLine(-1);
        mDebugBar.setVisibility(GONE);
        mInputMethodEnhanceBar.setVisibility(VISIBLE);
        mDebugging = false;
    }

    private void showErrorMessage(String msg) {
        Snackbar.make(EditorView.this, getResources().getString(R.string.text_error) + ": " + msg, Snackbar.LENGTH_LONG)
                .setAction(R.string.text_detail, v -> LogActivityKt.start(getContext()))
                .show();
    }

    @Override
    public void onHintClick(CodeCompletions completions, int pos) {
        CodeCompletion completion = completions.get(pos);
        mEditor.insert(completion.getInsertText());
    }

    @Override
    public void onHintLongClick(CodeCompletions completions, int pos) {
        CodeCompletion completion = completions.get(pos);
        if (Objects.equals(completion.getHint(), "/")) {
            getEditor().toggleComment();
            return;
        }
        if (completion.getUrl() == null) return;

        SharedPreferences sp = EditorAppManager.Companion.getSaveStatus(getContext());
        String name = sp.getString(EditorAppManager.DocumentSourceKEY, DocumentSource.DOC_V1.name());
        DocumentSource source;
        try {
            source = DocumentSource.valueOf(name);
        } catch (IllegalArgumentException e) {
            source = DocumentSource.DOC_V1;
        }

        String url = completion.getUrl();
        String finalUrl;
        if (source.isLocal()) {
            EditorAppManager.Companion.switchDocument(mDocsWebView.getWebView(), source);
            finalUrl = "http://localhost:8080/" + url;
        } else {
            finalUrl = source.getUri() + url;
        }

        showManual(finalUrl, completion.getHint());
    }


    private void showManual(String absUrl, String title) {
        // 不再拼接！直接使用传进来的完整地址！
        new ManualDialog(getContext())
                .title(title)
                .url(absUrl)
                .pinToLeft(v -> {
                    WebView webView = mDocsWebView.getWebView();
                    webView.loadUrl(absUrl);
                    // 强制滚动定位（绝杀）
                    try {
                        if (absUrl.contains("id=")) {
                            webView.postDelayed(() -> {
                                String id = absUrl.split("id=")[1];
                                webView.evaluateJavascript(
                                        "document.getElementById('" + id + "').scrollIntoView()",
                                        null
                                );
                            }, 100);
                        }
                    } catch (Exception ignored) {
                        // 出错也不崩溃
                    }
                    mDrawerLayout.openDrawer(GravityCompat.START);

                })
                .show();
    }

    @Override
    public void onModuleLongClick(Module module) {
        SharedPreferences sp = EditorAppManager.Companion.getSaveStatus(getContext());
        String name = sp.getString(EditorAppManager.DocumentSourceKEY, DocumentSource.DOC_V1.name());
        DocumentSource source;
        try {
            source = DocumentSource.valueOf(name);
        } catch (IllegalArgumentException e) {
            source = DocumentSource.DOC_V1;
        }

        String url = module.getUrl();
        String finalUrl;
        if (source.isLocal()) {
            EditorAppManager.Companion.switchDocument(mDocsWebView.getWebView(), source);
            finalUrl = "http://localhost:8080/" + url;
        } else {
            finalUrl = source.getUri() + url;
        }

        showManual(finalUrl, module.getName());
    }

    @Override
    public void onPropertyClick(Module m, Property property) {
        String p = property.getKey();
        if (!property.isVariable()) {
            p = p + "()";
        }
        //全局则,直接插入,否则 键+.+属性名
        if (property.isGlobal()) {
            mEditor.insert(p);
        } else {
            mEditor.insert(m.getName() + "." + p);
        }
        // 不是是变量
        if (!property.isVariable()) {
            //是否往左移动一格  ture 就是不给移动,  写 false 就是给移动  不写默认false是移动的
            if (!property.isStationary()) {
                mEditor.moveCursor(-1);
            }
        }
        //隐藏函数面板
        mFunctionsKeyboardHelper.hideFunctionsLayout(true);
    }

    @Override
    public void onPropertyLongClick(Module m, Property property) {

        // 1. 读取保存的文档源（在线/本地/本地2）
        SharedPreferences sp = EditorAppManager.Companion.getSaveStatus(getContext());
        String name = sp.getString(EditorAppManager.DocumentSourceKEY, DocumentSource.DOC_V1.name());
        DocumentSource source;
        try {
            source = DocumentSource.valueOf(name);
        } catch (IllegalArgumentException e) {
            source = DocumentSource.DOC_V1; // 兜底默认本地文档1
        }

        // 2. 完全保留你原有的url判断逻辑
        String url;
        if (TextUtils.isEmpty(property.getUrl())) {
            url = m.getUrl();
        } else {
            url = property.getUrl();
        }

        // 3. 核心修复：根据文档源，正确拼接最终地址 + 启动服务器
        String finalUrl;
        if (source.isLocal()) {
            // 本地文档：先复用switchDocument启动服务器，再拼接相对路径
            EditorAppManager.Companion.switchDocument(mDocsWebView.getWebView(), source);
            finalUrl = "http://localhost:8080/" + url;
        } else {
            // 在线文档：在线地址
            finalUrl = source.getUri() + url;
        }

        // 4. 打开文档
        showManual(finalUrl, property.getKey());


    }

    // @+id/toggle_change onToggle_changeClick
    public void setToggle_change() {
        // 从偏好设置中读取切换状态并恢复
        SharedPreferences sp = getContext().getSharedPreferences("EditorView", Context.MODE_PRIVATE);
        boolean isToggled = sp.getBoolean("toggle_state", false);
        if (isToggled) {
            // 如果之前是切换状态，切换到显示控制按钮
            mCodeCompletionBar.setVisibility(View.GONE);
            mFirstRowButtons.setVisibility(View.VISIBLE);
            mSecondRowButtons.setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mSymbolBar.getLayoutParams();
            params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
            params.addRule(RelativeLayout.RIGHT_OF, R.id.first_row_buttons);
            params.height = dpToPx(70);
            mSymbolBar.setLayoutParams(params);
        }

        mToggleChange.setOnClickListener(v -> {
            // 切换代码补全栏和控制按钮的可见性
            if (mCodeCompletionBar.getVisibility() == View.VISIBLE) {
                // 隐藏代码补全栏，显示控制按钮
                mCodeCompletionBar.setVisibility(View.GONE);// 代码补全栏隐藏
                mFirstRowButtons.setVisibility(View.VISIBLE);// 第一行按钮显示
                mSecondRowButtons.setVisibility(View.VISIBLE);// 第二行按钮显示

                // 设置symbol_bar在复制按钮右边
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mSymbolBar.getLayoutParams();
                params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                // params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE); // 靠顶部
                params.addRule(RelativeLayout.RIGHT_OF, R.id.first_row_buttons); // 设置在第一行按钮的右边
                // 设置高度为35dp
                params.height = dpToPx(70);
                mSymbolBar.setLayoutParams(params);

                // 保存切换状态
                sp.edit().putBoolean("toggle_state", true).apply();
            } else {

                mFirstRowButtons.setVisibility(View.GONE);// 第一行按钮隐藏
                mSecondRowButtons.setVisibility(View.GONE);// 第二行按钮隐藏

                // 恢复symbol_bar到默认位置
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mSymbolBar.getLayoutParams();
                params.removeRule(RelativeLayout.RIGHT_OF);
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                // params.addRule(RelativeLayout.BELOW, R.id.functions); // 设置在functions的下边
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE); // 靠底部
                // params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
                // 设置高度为35dp
                params.height = dpToPx(35);
                mSymbolBar.setLayoutParams(params);
                // 显示代码补全栏，隐藏控制按钮
                mCodeCompletionBar.setVisibility(View.VISIBLE);// 代码补全栏显示

                // 保存切换状态
                sp.edit().putBoolean("toggle_state", false).apply();
            }
        });

        // 设置控制按钮的点击监听
        setControlButtonListeners();
    }

    /**
     * 将 dp 值转换为像素值
     *
     * @param dp dp 值
     * @return 像素值
     */
    private int dpToPx(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setControlButtonListeners() {
        // 选择按钮点击事件 - 智能递进式选择
        mToggledSelect.setOnClickListener(v -> {
            EditText editText = mEditor.getCodeEditText();
            int start = editText.getSelectionStart();
            int end = editText.getSelectionEnd();

            // 重置选择层级如果没有选择或选择范围发生变化
            if (start == end) {
                mSelectionLevel = 0;
            }

            mSelectionLevel++;

            switch (mSelectionLevel) {
                case 1:
                    // 第1层：选择当前单词
                    selectCurrentWord(editText);
                    break;
                case 2:
                    // 第2层：选择当前行
                    selectCurrentLine(editText);
                    break;
                case 3:
                    // 第3层：选择括号对
                    selectBracketPair(editText);
                    break;
                case 4:
                    // 第4层：选择函数
                    selectCurrentFunction(editText);
                    break;
                case 5:
                    // 第5层：选择整个文件
                    selectEntireFile(editText);
                    break;
                default:
                    // 重置选择层级
                    mSelectionLevel = 1;
                    selectCurrentWord(editText);
                    break;
            }
        });

        // 上移按钮点击事件
        mToggledUp.setOnClickListener(v -> {
            mEditor.moveUpLine();
        });

        // 复制按钮点击事件
        mToggledCopy.setOnClickListener(v -> {
            mEditor.smartCopy();
        });

        // 空格键点击事件
        mToggledSpace.setOnClickListener(v -> {
            mEditor.insert("\t");
        });

        // 左移按钮点击事件
        mToggledLeft.setOnClickListener(v -> {
            mEditor.moveCursor(-1);
        });

        // 下移按钮点击事件
        mToggledDown.setOnClickListener(v -> {
            mEditor.moveDownLine();
        });

        // 右移按钮点击事件
        mToggledRight.setOnClickListener(v -> {
            mEditor.moveCursor(1);
        });
    }

    /**
     * 选择当前单词（智能识别编程语言标识符），如果没有单词则选择整行
     */
    private void selectCurrentWord(EditText editText) {
        int start = editText.getSelectionStart();
        CharSequence text = editText.getText();

        // 智能识别：包括字母、数字、下划线、美元符号、中文等
        while (start > 0 && isIdentifierChar(text.charAt(start - 1))) {
            start--;
        }

        int end = editText.getSelectionEnd();
        while (end < text.length() && isIdentifierChar(text.charAt(end))) {
            end++;
        }

        // 如果没有选择到任何内容，选择整行
        if (start == end) {
            int line = editText.getLayout().getLineForOffset(start);
            int lineStart = editText.getLayout().getLineStart(line);
            int lineEnd = editText.getLayout().getLineEnd(line);
            editText.setSelection(lineStart, lineEnd);
           // Toast.makeText(getContext(), "已选择行", Toast.LENGTH_SHORT).show();
            mSelectionLevel = 1; // 重置选择层级，下次点击会重新从单词开始
        } else {
            editText.setSelection(start, end);
           // Toast.makeText(getContext(), "已选择单词", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 判断字符是否为标识符字符（编程语言中的变量名字符）
     */
    private boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS;
    }

    /**
     * 选择当前行
     */
    private void selectCurrentLine(EditText editText) {
        int start = editText.getSelectionStart();
        int line = editText.getLayout().getLineForOffset(start);
        int lineStart = editText.getLayout().getLineStart(line);
        int lineEnd = editText.getLayout().getLineEnd(line);
        editText.setSelection(lineStart, lineEnd);
       // Toast.makeText(getContext(), "已选择行", Toast.LENGTH_SHORT).show();
    }

    /**
     * 选择括号对（使用栈算法，参考 VSCode 实现）
     */
    private void selectBracketPair(EditText editText) {
        int cursor = editText.getSelectionStart();
        CharSequence text = editText.getText();

        // 查找光标所在位置最近的括号对
        int[] bracketPair = findBracketPair(text, cursor);

        if (bracketPair != null) {
            editText.setSelection(bracketPair[0], bracketPair[1] + 1);
           // Toast.makeText(getContext(), "已选择括号对", Toast.LENGTH_SHORT).show();
        } else {
            // 如果没有找到括号对，尝试选择代码块
            selectCodeBlock(editText);
        }
    }

    /**
     * 查找包含光标的括号对（栈算法，参考 VSCode 实现）
     */
    private int[] findBracketPair(CharSequence text, int cursor) {
        final String brackets = "(){}[]";
        java.util.Stack<int[]> stack = new java.util.Stack<>();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int bracketIndex = brackets.indexOf(c);

            if (bracketIndex != -1) {
                if (bracketIndex % 2 == 0) {
                    // 左括号入栈
                    stack.push(new int[]{i, bracketIndex});
                } else {
                    // 右括号，尝试匹配
                    if (!stack.isEmpty()) {
                        int[] top = stack.peek();
                        if (top[1] == bracketIndex - 1) {
                            stack.pop();
                            int start = top[0];
                            int end = i;
                            if (cursor >= start && cursor <= end) {
                                return new int[]{start, end};
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * 选择代码块（找不到括号对时的回退方案）
     */
    private void selectCodeBlock(EditText editText) {
        int cursor = editText.getSelectionStart();
        CharSequence text = editText.getText();

        // 找到光标所在行的信息
        int line = editText.getLayout().getLineForOffset(cursor);
        int lineStart = editText.getLayout().getLineStart(line);

        // 查找当前行之前的非空字符
        int blockStart = lineStart;
        for (int i = lineStart; i < cursor && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' || c == '[' || c == '(') {
                blockStart = i;
                break;
            }
            if (!Character.isWhitespace(c)) {
                break;
            }
        }

        // 使用栈匹配算法找到对应的结束括号
        int blockEnd = findMatchingBlockEnd(text, blockStart);

        if (blockEnd == -1) {
            // 如果没有找到匹配的块，选择整行
            int lineEnd = editText.getLayout().getLineEnd(line);
            editText.setSelection(lineStart, lineEnd);
            //Toast.makeText(getContext(), "已选择行", Toast.LENGTH_SHORT).show();
        } else {
            // 选择从开始括号到结束括号的内容
            editText.setSelection(blockStart, blockEnd + 1);
           // Toast.makeText(getContext(), "已选择代码块", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 使用栈算法找到块的结束位置
     */
    private int findMatchingBlockEnd(CharSequence text, int start) {
        if (start >= text.length()) return -1;

        char startChar = text.charAt(start);
        char expectedEnd = getMatchingBracket(startChar);
        if (expectedEnd == 0) return -1;

        int depth = 1;
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == startChar) {
                depth++;
            } else if (c == expectedEnd) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 获取匹配的括号字符
     */
    private char getMatchingBracket(char bracket) {
        switch (bracket) {
            case '{': return '}';
            case '[': return ']';
            case '(': return ')';
            case '}': return '{';
            case ']': return '[';
            case ')': return '(';
            default: return 0;
        }
    }

    /**
     * 选择匹配的括号对
     */
    private void selectMatchingBrackets(EditText editText) {
        int cursor = editText.getSelectionStart();
        CharSequence text = editText.getText();

        if (cursor <= 0 || cursor >= text.length()) {
          // Toast.makeText(getContext(), "无法找到匹配的括号", Toast.LENGTH_SHORT).show();
            return;
        }

        char c = text.charAt(cursor);
        int start = -1, end = -1;

        // 检查光标是否在括号上
        if (isBracket(c)) {
            if (isOpeningBracket(c)) {
                // 光标在开始括号上，查找结束括号
                start = cursor;
                end = findMatchingBracketEnd(text, cursor);
            } else {
                // 光标在结束括号上，查找开始括号
                end = cursor;
                start = findMatchingBracketStart(text, cursor);
            }
        } else {
            // 光标不在括号上，检查前一个和后一个字符
            char prev = text.charAt(cursor - 1);
            char next = text.charAt(cursor);

            if (isBracket(prev)) {
                if (isOpeningBracket(prev)) {
                    start = cursor - 1;
                    end = findMatchingBracketEnd(text, cursor - 1);
                } else {
                    end = cursor - 1;
                    start = findMatchingBracketStart(text, cursor - 1);
                }
            } else if (isBracket(next)) {
                if (isOpeningBracket(next)) {
                    start = cursor;
                    end = findMatchingBracketEnd(text, cursor);
                } else {
                    end = cursor;
                    start = findMatchingBracketStart(text, cursor);
                }
            }
        }

        if (start != -1 && end != -1) {
            // 包括括号本身
            editText.setSelection(start, end + 1);
            //Toast.makeText(getContext(), "已选择括号对", Toast.LENGTH_SHORT).show();
        } else {
            //Toast.makeText(getContext(), "无法找到匹配的括号", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 判断是否为括号字符
     */
    private boolean isBracket(char c) {
        return c == '{' || c == '[' || c == '(' || c == '}' || c == ']' || c == ')';
    }

    /**
     * 判断是否为开始括号
     */
    private boolean isOpeningBracket(char c) {
        return c == '{' || c == '[' || c == '(';
    }

    /**
     * 查找结束括号的位置（使用栈算法）
     */
    private int findMatchingBracketEnd(CharSequence text, int start) {
        return findMatchingBlockEnd(text, start);
    }

    /**
     * 查找开始括号的位置
     */
    private int findMatchingBracketStart(CharSequence text, int end) {
        if (end >= text.length()) return -1;

        char endChar = text.charAt(end);
        char expectedStart = getMatchingBracket(endChar);
        if (expectedStart == 0) return -1;

        int depth = 1;
        for (int i = end - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == expectedStart) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            } else if (isBracket(c) && !isOpeningBracket(c)) {
                // 遇到嵌套的结束括号，深度增加
                depth++;
            }
        }
        return -1;
    }

    /**
     * 选择当前函数（参考 VSCode 的函数识别算法）
     */
    private void selectCurrentFunction(EditText editText) {
        int cursor = editText.getSelectionStart();
        int line = editText.getLayout().getLineForOffset(cursor);
        CharSequence text = editText.getText();

        // 从当前行向上查找函数定义
        for (int i = line; i >= 0; i--) {
            int lineStart = editText.getLayout().getLineStart(i);
            int lineEnd = editText.getLayout().getLineEnd(i);
            String lineText = text.subSequence(lineStart, lineEnd).toString();

            // 匹配函数定义：function、class、const/let/var + 函数名
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\\b(function|class|const\\s+\\w+\\s*=|let\\s+\\w+\\s*=|var\\s+\\w+\\s*=|=>)\\s*\\w*\\s*\\("
            );
            java.util.regex.Matcher matcher = pattern.matcher(lineText);

            if (matcher.find()) {
                // 找到函数定义，查找函数体的结束大括号
                int functionStart = lineStart + matcher.start();
                int functionEnd = findFunctionEnd(text, functionStart);

                if (functionEnd != -1) {
                    editText.setSelection(functionStart, functionEnd + 1);
                    //Toast.makeText(getContext(), "已选择函数", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        // 如果没有找到函数，选择整个文件
        selectEntireFile(editText);
        //Toast.makeText(getContext(), "已选择整个文件", Toast.LENGTH_SHORT).show();
    }

    /**
     * 查找函数体的结束位置（大括号计数，参考 VSCode 实现）
     */
    private int findFunctionEnd(CharSequence text, int start) {
        // 找到起始大括号
        int braceStart = -1;
        for (int i = start; i < text.length(); i++) {
            if (text.charAt(i) == '{') {
                braceStart = i;
                break;
            }
        }

        if (braceStart == -1) return -1;

        // 使用栈算法计算大括号匹配
        int depth = 1;
        for (int i = braceStart + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * 选择整个文件
     */
    private void selectEntireFile(EditText editText) {
        editText.selectAll();
       // Toast.makeText(getContext(), "已选择整个文件", Toast.LENGTH_SHORT).show();
    }


    public int getScriptExecutionId() {
        return mScriptExecutionId;
    }

    @Nullable
    public ScriptExecution getScriptExecution() {
        return AutoJs.getInstance().getScriptEngineService().getScriptExecution(mScriptExecutionId);
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        Parcelable superData = super.onSaveInstanceState();
        bundle.putParcelable("super_data", superData);
        bundle.putInt("script_execution_id", mScriptExecutionId);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        Bundle bundle = (Bundle) state;
        Parcelable superData = bundle.getParcelable("super_data");
        mScriptExecutionId = bundle.getInt("script_execution_id", ScriptExecution.NO_ID);
        super.onRestoreInstanceState(superData);
        setMenuItemStatus(R.id.run, mScriptExecutionId == ScriptExecution.NO_ID);
    }

    public void destroy() {
        mEditor.destroy();
        mAutoCompletion.shutdown();
    }
}
