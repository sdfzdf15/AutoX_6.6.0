package org.autojs.autojs.ui.edit.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.Toast;

import android.widget.EditText; // 导入EditText类，用于文本编辑


import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.snackbar.Snackbar;
import com.stardust.autojs.script.JsBeautifier;
import com.stardust.util.ClipboardUtil;
import com.stardust.util.TextUtils;
import com.stardust.util.ViewUtils;

import org.autojs.autojs.Pref;
import org.autojs.autojs.ui.edit.theme.Theme;
import org.autojs.autoxjs.R;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.reactivex.Observable;

/**
 * Copyright 2018 WHO<980008027@qq.com>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Modified by project: https://github.com/980008027/JsDroidEditor
 */
public class CodeEditor extends HVScrollView {

    public static class CheckedPatternSyntaxException extends Exception {
        public CheckedPatternSyntaxException(PatternSyntaxException cause) {
            super(cause);
        }
    }

    public interface CursorChangeCallback {

        void onCursorChange(String line, int ch);

    }


    private CodeEditText mCodeEditText;
    private TextViewUndoRedo mTextViewRedoUndo;
    private JavaScriptHighlighter mJavaScriptHighlighter;
    private Theme mTheme;
    private JsBeautifier mJsBeautifier;
    private MaterialDialog mProcessDialog;
    private ScaleGestureDetector detector;
    private CharSequence mReplacement = "";
    private String mKeywords;
    private Matcher mMatcher;
    private int mFoundIndex = -1;
    private  Integer mInitialStartCursorCol = null; // 记录最开始的列位置（用于上移选择）
    private  Integer mInitialEndCursorCol = null; // 记录最开始的结束列位置（用于下移选择）
    private int left_right_Active = 0; // 0: 都没被长按, 1: 左移被长按, 2: 右移被长按
    private int mSelectionLevel = 0; // 选择层级
    public CodeEditor(Context context) {
        super(context);
        init();
    }

    public CodeEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        //setFillViewport(true);
        inflate(getContext(), R.layout.code_editor, this);
        mCodeEditText = findViewById(R.id.code_edit_text);
        mCodeEditText.addTextChangedListener(new AutoIndent(mCodeEditText));
        mTextViewRedoUndo = new TextViewUndoRedo(mCodeEditText);
        mJavaScriptHighlighter = new JavaScriptHighlighter(mTheme, mCodeEditText);
        mJsBeautifier = new JsBeautifier(this, "js/js-beautify");
        detector = new ScaleGestureDetector(getContext(),getScaleGestureListener());
    }

    public ScaleGestureDetector.OnScaleGestureListener getScaleGestureListener() {
        return new ScaleGestureDetector.OnScaleGestureListener() {
            private double mLastScaleFactor = 1.0;
            private int mLastTextSize;
            private final int mMinTextSize = Integer.parseInt(getContext().getString(R.string.text_size_min_value));
            private final int mMaxTextSize = Integer.parseInt(getContext().getString(R.string.text_size_max_value));;
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                double currentFactor = Math.floor(detector.getScaleFactor() * 10) / 10;
                if (currentFactor > 0 && mLastScaleFactor != currentFactor) {
                    int currentTextSize = mLastTextSize + (currentFactor > mLastScaleFactor ? 1 : -1);
                    mLastTextSize = Math.max(mMinTextSize, Math.min(mMaxTextSize, currentTextSize));
                    mCodeEditText.setTextSize(mLastTextSize);
                    mLastScaleFactor = currentFactor;
                }
                mCodeEditText.invalidate();
                return false;
            }
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                mLastTextSize =Pref.getEditorTextSize((int) ViewUtils.pxToSp(getContext(), (int) mCodeEditText.getTextSize()));;
                return true;
            }
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                mLastScaleFactor = 1.0;
                Pref.setEditorTextSize(mLastTextSize);
            }
        };
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //缩放手势检测
        detector.onTouchEvent(event);
        //只拦截手势传递
        return detector.isInProgress() || super.onTouchEvent(event);
    }

    public Observable<Integer> getLineCount() {
        return Observable.just(mCodeEditText.getLayout().getLineCount());
    }

    public void copyLine() {
        int line = LayoutHelper.getLineOfChar(mCodeEditText.getLayout(), mCodeEditText.getSelectionStart());
        if (line < 0 || line >= mCodeEditText.getLayout().getLineCount())
            return;
        CharSequence lineText = mCodeEditText.getText().subSequence(mCodeEditText.getLayout().getLineStart(line),
                mCodeEditText.getLayout().getLineEnd(line));
        ClipboardUtil.setClip(getContext(), lineText);
        Snackbar.make(this, R.string.text_already_copy_to_clip, Snackbar.LENGTH_SHORT).show();
    }


    public void deleteLine() {
        int line = LayoutHelper.getLineOfChar(mCodeEditText.getLayout(), mCodeEditText.getSelectionStart());
        if (line < 0 || line >= mCodeEditText.getLayout().getLineCount())
            return;
        mCodeEditText.getText().replace(mCodeEditText.getLayout().getLineStart(line),
                mCodeEditText.getLayout().getLineEnd(line), "");
    }
    public void smartCopy() { // 智能复制：复制选中内容或当前行
        int start = mCodeEditText.getSelectionStart();
        int end = mCodeEditText.getSelectionEnd();

        // 检查是否有光标
        if (start == -1 || end == -1) {
            return; // 没有光标，不复制
        }

        CharSequence text;

        if (start != end) {
            // 有选中内容，复制选中的内容
            text = mCodeEditText.getText().subSequence(start, end);
        } else {
            // 没有选中内容，复制光标所在行
            int line = LayoutHelper.getLineOfChar(mCodeEditText.getLayout(), start); // 获取当前光标所在行
            if (line < 0 || line >= mCodeEditText.getLayout().getLineCount()) // 如果行号无效
                return; // 直接返回
            text = mCodeEditText.getText().subSequence(mCodeEditText.getLayout().getLineStart(line), // 获取该行起始位置
                    mCodeEditText.getLayout().getLineEnd(line)); // 获取该行结束位置
        }

        ClipboardUtil.setClip(getContext(), text); // 复制到剪贴板
        Snackbar.make(this, R.string.text_already_copy_to_clip, Snackbar.LENGTH_SHORT).show(); // 显示复制成功提示
    }
    public void jumpToStart() {
        mCodeEditText.setSelection(0);
        smoothScrollTo(0, 0);
    }

    public void jumpToEnd() {
        mCodeEditText.setSelection(mCodeEditText.getText().length());

        int lastLine = mCodeEditText.getLayout().getLineCount() - 1;
        int lineTop = mCodeEditText.getLayout().getLineTop(lastLine);
        smoothScrollTo(0, lineTop);
    }

    public void jumpToLineStart() {
        int line = LayoutHelper.getLineOfChar(mCodeEditText.getLayout(), mCodeEditText.getSelectionStart());
        if (line < 0 || line >= mCodeEditText.getLayout().getLineCount())
            return;
        mCodeEditText.setSelection(mCodeEditText.getLayout().getLineStart(line));
    }

    public void jumpToLineEnd() {
        int line = LayoutHelper.getLineOfChar(mCodeEditText.getLayout(), mCodeEditText.getSelectionStart());
        if (line < 0 || line >= mCodeEditText.getLayout().getLineCount())
            return;
        mCodeEditText.setSelection(mCodeEditText.getLayout().getLineEnd(line) - 1);

    }


    public void moveUpLine(int left_right_Active) { // 向上移动一行，保持列位置不变
        int start = mCodeEditText.getSelectionStart(); // 这个值是从文本开头开始计算的字符偏移量，从 0 开始计数 (选中文本的起始位置'开始箭头'的索引)
        int end = mCodeEditText.getSelectionEnd(); // 这个值是从文本开头开始计算的字符偏移量，从 0 开始计数 (选中文本的结束位置'结束箭头'的索引)
        boolean hasSelection = start != end; // 是否有选上了文本

        if (hasSelection) {
            int startLine = mCodeEditText.getLayout().getLineForOffset(start); // 开始箭头所在行(行索引)
            int endLine = mCodeEditText.getLayout().getLineForOffset(end); // 结束箭头所在行(行索引)

            if (mInitialStartCursorCol == null || mInitialEndCursorCol == null) {
                // (mCodeEditText.getLayout().getLineStart(startLine)返回的当前行的第一个字符,所在整个文本的位置(索引))
                mInitialStartCursorCol = start - mCodeEditText.getLayout().getLineStart(startLine); // 选中文本,当前行'开始箭头'的索引
                mInitialEndCursorCol = end - mCodeEditText.getLayout().getLineStart(endLine); // 选中文本,当前行'结束箭头'的索引
            }
            // (mCodeEditText.getLayout().getLineStart(endLine)返回的当前行的最后一个字符,所在整个文本的位置(索引))
            // int initialEndCol = end - mCodeEditText.getLayout().getLineStart(endLine); //
            int lineCount = mCodeEditText.getLayout().getLineCount(); // 总行数
            // 选中文本,当前行'结束箭头'的索引
            // 选中文本,'开始箭头'
            if (left_right_Active == 0 || left_right_Active == 1) {
                // 计算上一行的位置
                if (startLine > 0) {
                    int prevLine = startLine - 1; // 上一行的行号(行索引)
                    int prevLineStart = mCodeEditText.getLayout().getLineStart(prevLine); // 上一行第一个字符在整个文本中的位置(索引)
                    int prevLineEnd = mCodeEditText.getLayout().getLineEnd(prevLine); // 上一行最后一个字符在整个文本中的位置(索引)

                    // 计算上一行的长度
                    int prevLineLength = prevLineEnd - prevLineStart;
                    // 如果该行以换行符结尾，减去1
                    if (prevLineLength > 0 && mCodeEditText.getText().charAt(prevLineEnd - 1) == '\n') {
                        prevLineLength--;
                    }
                    // 计算新的开始列位置，使用最开始的列位置
                    int newStartCol = 0;/// 新的开始列位置(列索引)
                    if (prevLineLength <= 0) {
                        newStartCol = 0; // 上一行为空，新开始列位置为0
                        // } else if (prevLineLength < mInitialStartCursorCol) {
                        // newStartCol = prevLineLength; // 上一行的长度小于最开始的列位置，新开始列位置为上一行的长度
                        // } else if (prevLineLength >= mInitialStartCursorCol) {
                        // newStartCol = mInitialStartCursorCol; // 上一行的长度大于等于最开始的列位置，新开始列位置为最开始的列位置
                    } else {
                        // 短行自动适配行尾
                        newStartCol = Math.min(mInitialStartCursorCol, prevLineLength);
                    }

                    // 计算上一行的起始位置（用于设置光标）
                    int newStartPos = prevLineStart + newStartCol; // 新的开始位置 = 上一行起始位置 + 列偏移
                    // 设置位置（开始光标上移一行，结束光标保持不变）
                    mCodeEditText.setSelection(newStartPos, end);

                }
            } else if (endLine > startLine && endLine > 0) { // 选中文本,'结束箭头' 的缩小
                int prevLine = endLine - 1; // 结束箭头所在行(行索引)-1(上一行)
                int prevLineStart = mCodeEditText.getLayout().getLineStart(prevLine); // 上一行第一个字符在整个文本中的位置(索引)
                int prevLineEnd = mCodeEditText.getLayout().getLineEnd(prevLine); // 上一行最后一个字符在整个文本中的位置(索引)
                // 计算上一行的长度
                int prevLineLength = prevLineEnd - prevLineStart;
                if (prevLine == startLine && mInitialEndCursorCol <= mInitialStartCursorCol) {
                    return;
                } else {
                    // 如果该行以换行符结尾，减去1
                    if (prevLineLength > 0 && mCodeEditText.getText().charAt(prevLineEnd - 1) == '\n') {
                        prevLineLength--;
                    }
                    // 计算新的结束列位置（结束光标下移）
                    int newEndCol = 0; // 新的结束列位置
                    if (prevLineLength == 0) {
                        newEndCol = 0; // 下一行为空，新结束列位置为0
                    } else {
                        newEndCol = Math.min(mInitialEndCursorCol, prevLineLength);
                    }
                    // 计算下一行的结束位置（用于设置光标）
                    int newEndPos = prevLineStart + newEndCol; // 新的结束位置 = 下一行起始位置 + 列偏移
                    // 设置位置（结束光标下移一行，开始光标保持不变）
                    mCodeEditText.setSelection(start, newEndPos);
                }
            }

        } else {
            mInitialStartCursorCol = null; // 重置左光标所在行中的列位置
            mInitialEndCursorCol = null; // 重置右光标所在行中的列位置
            // 使用系统默认的光标移动行为
            KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP);
            mCodeEditText.dispatchKeyEvent(upEvent);
            mCodeEditText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP));
        }
    }

    public void moveDownLine(int left_right_Active) { // 向下移动一行，保持列位置不变
        int start = mCodeEditText.getSelectionStart(); // 获取选择的起始位置(列索引)
        int end = mCodeEditText.getSelectionEnd(); // 获取选择的结束位置(列索引)
        boolean hasSelection = start != end; // 是否有选上了文本

        if (hasSelection) {
            int startLine = mCodeEditText.getLayout().getLineForOffset(start); // 起始位置所在行(行索引)
            int endLine = mCodeEditText.getLayout().getLineForOffset(end); // 结束位置所在行(行索引)

            // 第一次多行选择时，记录最开始的开始和结束列位置
            if (mInitialEndCursorCol == null) {
                mInitialStartCursorCol = start - mCodeEditText.getLayout().getLineStart(startLine); // 选中文本,当前行'开始箭头'的索引
                mInitialEndCursorCol = end - mCodeEditText.getLayout().getLineStart(endLine); // 记录最开始的结束列位置
            }

            // int initialEndCol = end - mCodeEditText.getLayout().getLineStart(endLine); //
            // 记录最结束的列位置

            int lineCount = mCodeEditText.getLayout().getLineCount(); // 总行数
            // 选中文本,'结束箭头' 放大
            if (left_right_Active == 0 || left_right_Active == 2) {
                // 计算下一行的位置
                if (endLine < lineCount - 1) {
                    int nextLine = endLine + 1; // 结束行的下一行(行索引)
                    int nextLineStart = mCodeEditText.getLayout().getLineStart(nextLine); // 下一行的起始位置(列索引),表示下一行第一个字符在整个文本中的位置
                    int nextLineEnd = mCodeEditText.getLayout().getLineEnd(nextLine); // 下一行的结束位置(列索引),表示下一行最后一个字符在整个文本中的位置

                    // 计算下一行文本的长度
                    int nextLineLength = nextLineEnd - nextLineStart;
                    // 如果该行以换行符结尾，减去1
                    if (nextLineLength > 0 && mCodeEditText.getText().charAt(nextLineEnd - 1) == '\n') {
                        nextLineLength--;
                    }
                    // 计算新的结束列位置（结束光标下移）
                    int newEndCol = 0; // 新的结束列位置
                    if (nextLineLength == 0) {
                        newEndCol = 0; // 下一行为空，新结束列位置为0
                    } else {
                        newEndCol = Math.min(mInitialEndCursorCol, nextLineLength);
                    }
                    // 计算下一行的结束位置（用于设置光标）
                    int newEndPos = nextLineStart + newEndCol; // 新的结束位置 = 下一行起始位置 + 列偏移
                    // 设置位置（结束光标下移一行，开始光标保持不变）
                    mCodeEditText.setSelection(start, newEndPos);

                    // 滚动到新位置
                    // smoothScrollTo(0, mCodeEditText.getLayout().getLineTop(nextLine));
                }
            } else if (startLine < endLine && startLine < lineCount - 1) { // 选中文本,'开始箭头'缩小
                int nextLine = startLine + 1; // 开始箭头所在行(行索引)+1(下一行)
                int nextLineStart = mCodeEditText.getLayout().getLineStart(nextLine); // 下一行的起始位置(列索引),表示下一行第一个字符在整个文本中的位置
                int nextLineEnd = mCodeEditText.getLayout().getLineEnd(nextLine); // 下一行的结束位置(列索引),表示下一行最后一个字符在整个文本中的位置
                // 计算下一行文本的长度
                int nextLineLength = nextLineEnd - nextLineStart;
                if (nextLine == endLine && mInitialStartCursorCol >= mInitialEndCursorCol) {
                    return;
                } else {
                    // 如果该行以换行符结尾，减去1
                    if (nextLineLength > 0 && mCodeEditText.getText().charAt(nextLineEnd - 1) == '\n') {
                        nextLineLength--;
                    }
                    // 计算新的结束列位置（结束光标下移）
                    int newStartCol = 0; // 新的结束列位置
                    if (nextLineLength == 0) {
                        newStartCol = 0; // 下一行为空，新结束列位置为0
                    } else {
                        newStartCol = Math.min(mInitialStartCursorCol, nextLineLength);
                    }
                    // 计算下一行的结束位置（用于设置光标）
                    int newStartPos = nextLineStart + newStartCol; // 新的结束位置 = 下一行起始位置 + 列偏移
                    // 设置位置（结束光标下移一行，开始光标保持不变）
                    mCodeEditText.setSelection(newStartPos, end);
                }

            }
        } else {
            mInitialStartCursorCol = null; // 重置左光标所在行中的列位置
            mInitialEndCursorCol = null; // 重置右光标所在行中的列位置

            // 使用系统默认的光标移动行为
            KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
            mCodeEditText.dispatchKeyEvent(downEvent);
            mCodeEditText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN));
        }
    }

    public void setTheme(Theme theme) {
        mTheme = theme;
        setBackgroundColor(mTheme.getBackgroundColor());
        mJavaScriptHighlighter.setTheme(theme);
        mJavaScriptHighlighter.updateTokens(mCodeEditText.getText().toString());
        mCodeEditText.setTheme(mTheme);
        invalidate();
    }

    public boolean isTextChanged() {
        return mTextViewRedoUndo.isTextChanged();
    }

    public boolean canUndo() {
        return mTextViewRedoUndo.canUndo();
    }

    public boolean canRedo() {
        return mTextViewRedoUndo.canRedo();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        mCodeEditText.postInvalidate();
    }

    public CodeEditText getCodeEditText() {
        return mCodeEditText;
    }

    public void setInitialText(String text) {
        mCodeEditText.setText(text);
        mTextViewRedoUndo.setDefaultText(text);
    }

    public void jumpTo(int line, int col) {
        Layout layout = mCodeEditText.getLayout();
        if (line < 0 || (layout != null && line >= layout.getLineCount())) {
            return;
        }
        mCodeEditText.setSelection(mCodeEditText.getLayout().getLineStart(line) + col);

        int lineTop = mCodeEditText.getLayout().getLineTop(line);
        smoothScrollTo(0, lineTop);
    }

    public void setReadOnly(boolean readOnly) {
        mCodeEditText.setEnabled(!readOnly);
    }

    public void setRedoUndoEnabled(boolean enabled) {
        mTextViewRedoUndo.setEnabled(enabled);
    }

    public void setProgress(boolean progress) {
        if (progress) {
            if (mProcessDialog != null) {
                mProcessDialog.dismiss();
            }
            mProcessDialog = new MaterialDialog.Builder(getContext())
                    .content(R.string.text_processing)
                    .progress(true, 0)
                    .cancelable(false)
                    .show();
        } else {
            if (mProcessDialog != null) {
                mProcessDialog.dismiss();
                mProcessDialog = null;
            }
        }

    }

    public void setText(String text) {
        mCodeEditText.setText(text);
    }

    public void addCursorChangeCallback(CursorChangeCallback callback) {
        mCodeEditText.addCursorChangeCallback(callback);
    }

    public boolean removeCursorChangeCallback(CursorChangeCallback callback) {
        return mCodeEditText.removeCursorChangeCallback(callback);
    }


    public void undo() {
        mTextViewRedoUndo.undo();
    }

    public void redo() {
        mTextViewRedoUndo.redo();
    }

    public void find(String keywords, boolean usingRegex) throws CheckedPatternSyntaxException {
        if (usingRegex) {
            try {
                mMatcher = Pattern.compile(keywords).matcher(mCodeEditText.getText());
            } catch (PatternSyntaxException e) {
                throw new CheckedPatternSyntaxException(e);
            }
            mKeywords = null;
        } else {
            mKeywords = keywords;
            mMatcher = null;
        }
        findNext();
    }

    public void replace(String keywords, String replacement, boolean usingRegex) throws CheckedPatternSyntaxException {
        mReplacement = replacement == null ? "" : replacement;
        find(keywords, usingRegex);
    }

    public void replaceAll(String keywords, String replacement, boolean usingRegex) throws CheckedPatternSyntaxException {
        if (!usingRegex) {
            keywords = Pattern.quote(keywords);
        }
        String text = mCodeEditText.getText().toString();
        try {
            text = text.replaceAll(keywords, replacement);
        } catch (PatternSyntaxException e) {
            throw new CheckedPatternSyntaxException(e);
        }
        setText(text);
    }

    public void findNext() {
        int foundIndex;
        if (mMatcher == null) {
            if (mKeywords == null)
                return;
            foundIndex = TextUtils.indexOf(mCodeEditText.getText(), mKeywords, mFoundIndex + 1);
            if (foundIndex >= 0)
                mCodeEditText.setSelection(foundIndex, foundIndex + mKeywords.length());
        } else if (mMatcher.find(mFoundIndex + 1)) {
            foundIndex = mMatcher.start();
            mCodeEditText.setSelection(foundIndex, foundIndex + mMatcher.group().length());
        } else {
            foundIndex = -1;
        }
        if (foundIndex < 0 && mFoundIndex >= 0) {
            mFoundIndex = -1;
            findNext();
        } else {
            mFoundIndex = foundIndex;
        }
    }

    public void findPrev() {
        if (mMatcher != null) {
            Toast.makeText(getContext(), R.string.error_regex_find_prev, Toast.LENGTH_SHORT).show();
            return;
        }
        int len = mCodeEditText.getText().length();
        if (mFoundIndex <= 0) {
            mFoundIndex = len;
        }
        int index = mCodeEditText.getText().toString().lastIndexOf(mKeywords, mFoundIndex - 1);
        if (index < 0) {
            if (mFoundIndex != len) {
                mFoundIndex = len;
                findPrev();
            }
            return;
        }
        mFoundIndex = index;
        mCodeEditText.setSelection(index, index + mKeywords.length());
    }

    public void replaceSelection() {
        mCodeEditText.getText().replace(mCodeEditText.getSelectionStart(), mCodeEditText.getSelectionEnd(), mReplacement);
    }

    public void beautifyCode() {
        setProgress(true);
        mJsBeautifier.beautify(mCodeEditText.getText().toString(), new JsBeautifier.Callback() {
            @Override
            public void onSuccess(String beautifiedCode) {
                setProgress(false);
                mCodeEditText.setText(beautifiedCode);
            }

            @Override
            public void onException(Exception e) {
                setProgress(false);
                e.printStackTrace();
            }
        });
    }


    public void insert(String insertText) {
        int selection = Math.max(mCodeEditText.getSelectionStart(), 0);
        mCodeEditText.getText().insert(selection, insertText);
    }

    public void insert(int line, String insertText) {
        int selection = mCodeEditText.getLayout().getLineStart(line);
        mCodeEditText.getText().insert(selection, insertText);
    }

    public void moveCursor(int dCh) {
        mCodeEditText.setSelection(mCodeEditText.getSelectionStart() + dCh);
    }

    public void moveCursor(int dCh, int left_right_Active) { // 移动光标
        int start = mCodeEditText.getSelectionStart();
        int end = mCodeEditText.getSelectionEnd();
        boolean hasSelection = start != end;

        if (hasSelection) {
            // 为0时，左移按钮扩选右光标，右移按钮扩选左光标
            if (left_right_Active == 0) {
                // 有选择时，调整选择范围
                if (dCh > 0) {
                    mInitialEndCursorCol = null; // 重置右光标所在行中的列位置
                    // 向右移动，扩展选择
                    int newEnd = Math.min(end + dCh, mCodeEditText.getText().length());
                    mCodeEditText.setSelection(start, newEnd);
                } else {
                    mInitialStartCursorCol = null; // 重置左光标所在行中的列位置
                    // 向左移动，收缩选择
                    int newStart = Math.max(start + dCh, 0);
                    mCodeEditText.setSelection(newStart, end);
                }
            } else if (left_right_Active == 1) {// 为1时，左移按钮增加左光标选择范围，右移按钮减少左光标选择范围
                // 有选择时，调整选择范围
                if (dCh > 0) {
                    mInitialStartCursorCol = null; // 重置左光标所在行中的列位置
                    // 向右移动，扩展选择
                    int newStart = Math.min(start + dCh, mCodeEditText.getText().length());
                    mCodeEditText.setSelection(newStart, end);
                } else {
                    mInitialStartCursorCol = null; // 重置左光标所在行中的列位置
                    // 向左移动，收缩选择
                    int newStart = Math.max(start + dCh, 0);
                    mCodeEditText.setSelection(newStart, end);
                }
            } else if (left_right_Active == 2) {// 为2时，左移按钮减少右光标选择范围，右移按钮增加右光标选择范围
                // 有选择时，调整选择范围
                if (dCh > 0) {
                    mInitialEndCursorCol = null; // 重置右光标所在行中的列位置
                    // 向右移动，扩展选择
                    int newEnd = Math.min(end + dCh, mCodeEditText.getText().length());
                    mCodeEditText.setSelection(start, newEnd);
                } else {
                    mInitialEndCursorCol = null; // 重置右光标所在行中的列位置
                    // 向左移动，收缩选择
                    int newEnd = Math.max(end + dCh, 0);
                    mCodeEditText.setSelection(start, newEnd);
                }
            }
        } else {
            // 无选择时，重置光标位置
            mInitialStartCursorCol = null; // 重置左光标所在行中的列位置
            mInitialEndCursorCol = null; // 重置右光标所在行中的列位置
            // 无选择时，移动光标
            int newPos = Math.max(0, Math.min(start + dCh, mCodeEditText.getText().length()));
            mCodeEditText.setSelection(newPos);
        }
    }
    public String getText() {
        return mCodeEditText.getText().toString();
    }

    public Observable<String> getSelection() {
        int s = mCodeEditText.getSelectionStart();
        int e = mCodeEditText.getSelectionEnd();
        if (s == e) {
            return Observable.just("");
        }
        return Observable.just(mCodeEditText.getText().toString().substring(s, e));
    }


    public void markTextAsSaved() {
        mTextViewRedoUndo.markTextAsUnchanged();
    }

    public LinkedHashMap<Integer, Breakpoint> getBreakpoints() {
        return mCodeEditText.getBreakpoints();
    }

    public void setDebuggingLine(int line) {
        mCodeEditText.setDebuggingLine(line);
    }

    public void setBreakpointChangeListener(BreakpointChangeListener listener) {
        mCodeEditText.setBreakpointChangeListener(listener);
    }

    public void addOrRemoveBreakpoint(int line) {
        if (!mCodeEditText.removeBreakpoint(line)) {
            mCodeEditText.addBreakpoint(line);
        }
    }

    public void addOrRemoveBreakpointAtCurrentLine() {
        int line = LayoutHelper.getLineOfChar(mCodeEditText.getLayout(), mCodeEditText.getSelectionStart());
        if (line < 0 || line >= mCodeEditText.getLayout().getLineCount())
            return;
        addOrRemoveBreakpoint(line);
    }

    public void removeAllBreakpoints() {
        mCodeEditText.removeAllBreakpoints();
    }

    public void destroy() {
        mJavaScriptHighlighter.shutdown();
        mJsBeautifier.shutdown();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int codeWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int codeHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (mCodeEditText.getMinWidth() != codeWidth || mCodeEditText.getMinWidth() != codeWidth) {
            mCodeEditText.setMinWidth(codeWidth);
            mCodeEditText.setMinHeight(codeHeight);
            invalidate();
        }
        super.onDraw(canvas);
    }

    public static class Breakpoint {

        public int line;
        public boolean enabled = true;

        public Breakpoint(int line) {
            this.line = line;
        }
    }

    public void toggleComment() {
        final String COMMENT_PREFIX = "//";
        Editable editText = Objects.requireNonNull(mCodeEditText.getText());
        int selectionStart = mCodeEditText.getSelectionStart();
        int selectionEnd = mCodeEditText.getSelectionEnd();

        int startLine = LayoutHelper.getLineOfChar(mCodeEditText.getLayout(), selectionStart);
        int endLine = LayoutHelper.getLineOfChar(mCodeEditText.getLayout(), selectionEnd);

        for (int currentLine = startLine; currentLine <= endLine; currentLine++) {
            int lineStart = mCodeEditText.getLayout().getLineStart(currentLine);
            int lineEnd = mCodeEditText.getLayout().getLineEnd(currentLine);

            String currentLineText = editText.toString().substring(lineStart, lineEnd);
            String modifiedLineText = currentLineText.replaceAll("^\\s*(/{2,})", "");

            if (!currentLineText.trim().startsWith(COMMENT_PREFIX)) {
                modifiedLineText = COMMENT_PREFIX + modifiedLineText;
            }

            editText.replace(lineStart, lineEnd, modifiedLineText);
        }
    }

    public interface BreakpointChangeListener {
        void onBreakpointChange(int line, boolean enabled);

        void onAllBreakpointRemoved(int count);
    }


    /**
     * 选择当前单词（智能识别编程语言标识符），如果没有单词则选择整行
     */
    public void selectCurrentWord() {
        EditText editText = mCodeEditText;
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
            Toast.makeText(getContext(), "已选择行", Toast.LENGTH_SHORT).show();
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
        return Character.isLetterOrDigit(c) || c == '_' || c == '$'
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS;
    }

    /**
     * 选择当前行
     */
    public void selectCurrentLine() {
        EditText editText = mCodeEditText;
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
    public void selectBracketPair() {
        EditText editText = mCodeEditText;
        int cursor = editText.getSelectionStart();
        CharSequence text = editText.getText();

        // 查找光标所在位置最近的括号对
        int[] bracketPair = findBracketPair(text, cursor);

        if (bracketPair != null) {
            editText.setSelection(bracketPair[0], bracketPair[1] + 1);
            // Toast.makeText(getContext(), "已选择括号对", Toast.LENGTH_SHORT).show();
        } else {
            // 如果没有找到括号对，尝试选择代码块
            selectCodeBlock();
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
                    stack.push(new int[] { i, bracketIndex });
                } else {
                    // 右括号，尝试匹配
                    if (!stack.isEmpty()) {
                        int[] top = stack.peek();
                        if (top[1] == bracketIndex - 1) {
                            stack.pop();
                            int start = top[0];
                            int end = i;
                            if (cursor >= start && cursor <= end) {
                                return new int[] { start, end };
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
    private void selectCodeBlock() {
        EditText editText = mCodeEditText;
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
            // Toast.makeText(getContext(), "已选择行", Toast.LENGTH_SHORT).show();
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
        if (start >= text.length())
            return -1;

        char startChar = text.charAt(start);
        char expectedEnd = getMatchingBracket(startChar);
        if (expectedEnd == 0)
            return -1;

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
            case '{':
                return '}';
            case '[':
                return ']';
            case '(':
                return ')';
            case '}':
                return '{';
            case ']':
                return '[';
            case ')':
                return '(';
            default:
                return 0;
        }
    }

    /**
     * 选择匹配的括号对
     */
    public void selectMatchingBrackets() {
        EditText editText = mCodeEditText;
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
            // Toast.makeText(getContext(), "已选择括号对", Toast.LENGTH_SHORT).show();
        } else {
            // Toast.makeText(getContext(), "无法找到匹配的括号", Toast.LENGTH_SHORT).show();
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
        if (end >= text.length())
            return -1;

        char endChar = text.charAt(end);
        char expectedStart = getMatchingBracket(endChar);
        if (expectedStart == 0)
            return -1;

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
    public void selectCurrentFunction() {
        EditText editText = mCodeEditText;
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
                    "\\b(function|class|const\\s+\\w+\\s*=|let\\s+\\w+\\s*=|var\\s+\\w+\\s*=|=>)\\s*\\w*\\s*\\(");
            java.util.regex.Matcher matcher = pattern.matcher(lineText);

            if (matcher.find()) {
                // 找到函数定义，查找函数体的结束大括号
                int functionStart = lineStart + matcher.start();
                int functionEnd = findFunctionEnd(text, functionStart);

                if (functionEnd != -1) {
                    editText.setSelection(functionStart, functionEnd + 1);
                    // Toast.makeText(getContext(), "已选择函数", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        // 如果没有找到函数，选择整个文件
        selectEntireFile();
        // Toast.makeText(getContext(), "已选择整个文件", Toast.LENGTH_SHORT).show();
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

        if (braceStart == -1)
            return -1;

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
    public void selectEntireFile() {
        mCodeEditText.selectAll();
        // Toast.makeText(getContext(), "已选择整个文件", Toast.LENGTH_SHORT).show();
    }

    /**
     * 执行智能递进式选择
     */
    public void performSmartSelection() {
        int start = mCodeEditText.getSelectionStart();
        int end = mCodeEditText.getSelectionEnd();
        if (mInitialStartCursorCol != null) {
            mInitialStartCursorCol = null;
        }
        if (mInitialEndCursorCol != null) {
            mInitialEndCursorCol = null;
        }

        // 重置选择层级如果没有选择或选择范围发生变化
        if (start == end) {
            mSelectionLevel = 0;
        }

        mSelectionLevel++;

        switch (mSelectionLevel) {
            case 1:
                // 第1层：选择当前单词
                selectCurrentWord();
                // 弹出系统文本选择菜单
                // editText.showContextMenu();
                break;
            case 2:
                // 第2层：选择当前行
                selectCurrentLine();
                break;
            case 3:
                // 第3层：选择括号对
                selectBracketPair();
                break;
            case 4:
                // 第4层：选择函数
                selectCurrentFunction();
                break;
            case 5:
                // 第5层：选择整个文件
                selectEntireFile();
                break;
            default:
                // 重置选择层级
                mSelectionLevel = 1;
                selectCurrentWord();
                break;
        }
    }

    /**
     * 获取左右光标激活状态
     */
    public int getLeftRightActive() {
        return left_right_Active;
    }

    /**
     * 设置左右光标激活状态
     */
    public void setLeftRightActive(int left_right_Active) {
        this.left_right_Active = left_right_Active;
    }

}

