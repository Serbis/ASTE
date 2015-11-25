package solkris.ru.aste;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.widget.EditText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import solkris.ru.aste.desc.FontStyle;
import solkris.ru.aste.desc.Keyword;
import solkris.ru.aste.desc.Pos;
import solkris.ru.aste.lexer.Dla;
import solkris.ru.aste.lexer.Lexer;
import solkris.ru.aste.lexer.Tag;
import solkris.ru.aste.lexer.Token;

/**
 * Created by serbis on 11.11.15.
 */
public class SyntaxEditTextOld extends EditText {
    /** Object of the lexical analyzer */
    private Lexer lexer = null;
    /** List of strings in html representation */
    private List<Spannable> htmlStrings = new ArrayList<Spannable>();
    /** A string representation of the array of tokens */
    private ArrayList<List<Token>> tokla = new ArrayList<>();;
    /** Current cursor position */
    private int curpos = 0;
    /** Current selected token */
    private Token currenttok = null;
    /** Position the current token in the array tokla */
    private Pos currentpos = null;
    /** Next token position*/
    private Pos np = new Pos(0, 0, 0);

    private Token pt = new Token(" ", 0, 0, false);

    private boolean constf = false;

    private Dla dla = new Dla();

    /**
     * Constructor 1.
     *
     * @param context Application context
     */
    public SyntaxEditTextOld(Context context) {
        super(context);
        init();
    }

    /**
     * Constructor 2.
     *
     * @param context Application context
     * @param attrs Attributes
     */
    public SyntaxEditTextOld(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Constructor 3.
     *
     * @param context Application context
     * @param attrs Attributes
     * @param defStyleAttr Style attributes
     */
    public SyntaxEditTextOld(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Overall initialization procedure for all constructors
     */
    public void init() {
        setSingleLine(false);
        setGravity(Gravity.TOP);
        currentpos = new Pos(1000, 1000, 0);
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (count > after) {//deleted one char
                    deleteOneChar();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > before) {//add one char
                    try {
                        String sub = s.subSequence(start, start + count).toString();

                        if (tokla.size() == 0) { //Если это первый символ ввода
                            tokla.add(new ArrayList<Token>());
                            Token tok = new Token(sub, 0, 1, false);
                            createToken(new Pos(0, 0, 0), tok);
                            return;
                        } else {
                            np = getNextTokenPos(currentpos, 1);
                        }

                        currenttok.constflag = constf;
                        final ArrayList<List<Token>> tkl = tokla;
                        final Token ct = currenttok;
                        final Pos cp = currentpos;
                        final Pos npp = np;
                        Dla.TokenOperation op = dla.trace(tkl, ct, cp, npp, sub);
                        switch (op.tokenOperationType) {
                            case TOKEN_ADD:
                                if (op.token.lexeme.equals("'") || op.token.lexeme.equals("\"")) {
                                    constf = !constf;
                                }
                                if (tokla.size() < op.pos.line + 1) {
                                    tokla.add(new ArrayList<Token>());
                                }
                                if (tokla.get(op.pos.line).size() <= op.pos.offset + 1) { //Если впереди нет токенов
                                    tokla.get(op.pos.line).add(op.token);
                                } else {
                                    tokla.get(op.pos.line).add(op.pos.offset - 1, op.token);
                                }
                                if (np != null) {
                                    resizeTokensOffset(new Pos(op.pos.line, op.pos.offset - 1, 0), 1);
                                }

                                replaceTokenInText(op.token, 0);
                                break;

                            case TOKEN_CHANGE:
                                tokla.get(op.pos.line).set(op.pos.offset, op.token);
                                if (np != null) {
                                    resizeTokensOffset(new Pos(op.pos.line, op.pos.offset, 0), 1);
                                }
                                replaceTokenInText(op.token, 0);
                                break;
                            case TOKEN_INSERT:

                                break;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }


    /**
     * Sets the global list of keywords
     *
     * @param list List of keywords
     */
    public void setKeywordList(List<Keyword> list) {
        Token.keywords = list;
        lexer = new Lexer();
    }

    /**
     * Sets a global style numerical tokens
     *
     * @param style Styles of numerical tokens
     */
    public void setNumbersStyle(FontStyle style) {
        Token.numbersStyle = style;
    }

    /**
     * Sets a global style constants tokens (Such as "...")
     *
     * @param style Styles of constants tokens
     */
    public void setConstantsStyle(FontStyle style) {
        Token.constantStyle = style;
    }

    /**
     * Sets a global style undefined text tokens
     *
     * @param style style of undefined text tokens
     */
    public void setTextStyle(FontStyle style) {
        Token.textStyle = style;
    }

    /**
     * Set some raw text. In the beginning there is a check whether the object
     * is a lexical analyzer. If not, then it creates. Then it sends text to
     * the lexical analyzer and receives a stream of tokens, which is based on
     * data about the number of rows in each token, breaks it into an array of
     * strings. Then invokes the method for forming an array of the token
     * string array, which then merges into a single row and set to
     * view.
     *
     * @param text Raw text
     */
    public void setText(String text) {
        int line = 0;

        List<Token> tokens = lexer.scanAll(text);
        List<Token> ts = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).line == line) {
                ts.add(tokens.get(i));
            } else {
                line = tokens.get(i).line;
                tokla.add(ts);
                ts = new ArrayList<>();
                ts.add(tokens.get(i));
            }
        }
        tokla.add(ts);
        htmlStrings = formateHtmlString(tokla);
        SpannableStringBuilder sp = new SpannableStringBuilder();
        for (int i = 0; i < htmlStrings.size(); i++) {
            sp.append(htmlStrings.get(i));
        }

        setText(sp, BufferType.EDITABLE);
        ;
    }

    /**
     * It creates an array of rows of tokens array of strings in html format
     * suitable for placing in the edittext.
     *
     * @param ta Array of tokens
     * @return Html array of strings
     */
    private List<Spannable> formateHtmlString(ArrayList<List<Token>> ta) {
        List<Spannable> list = new ArrayList<>();
        for (int i = 0; i < ta.size(); i++) {
            SpannableStringBuilder spannable = new SpannableStringBuilder();
            int offset = 0;
            for (int j = 0; j < ta.get(i).size(); j++) {
                Token tok = ta.get(i).get(j);
                spannable.append(tok.lexeme);
                spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#" + tok.fontStyle.color)), offset, offset + tok.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new RelativeSizeSpan(tok.fontStyle.size), offset, offset + tok.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new TypefaceSpan(tok.fontStyle.font), offset, offset + tok.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (tok.fontStyle.bold)
                    spannable.setSpan(new StyleSpan(Typeface.BOLD), offset, offset + tok.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (tok.fontStyle.italic)
                    spannable.setSpan(new StyleSpan(Typeface.ITALIC), offset, offset + tok.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (tok.fontStyle.underline)
                    spannable.setSpan(new UnderlineSpan(), offset, offset + tok.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                offset += tok.length;
            }
            list.add(spannable);
        }

        return list;
    }

    /**
     * It returns the position of the token which is currently under the
     * cursor. To search for loops through the whole array To search for
     * a token goes through the entire array tokla until the fall at the
     * right Range of displacement.
     *
     * @param pos Absolute position in the text
     * @return Pos object
     */
    private Pos getSelectedTokenPos(int pos) {
        for (int i = 0; i < tokla.size(); i++) {
            for (int j = 0; j < tokla.get(i).size(); j++) {
                if (pos + 1 > tokla.get(i).get(j).offset && pos + 1 <= tokla.get(i).get(j).offset + tokla.get(i).get(j).length) {
                    return new Pos(i, j, pos + 1 - tokla.get(i).get(j).offset);
                }
            }
        }

        return null;
    }

    /**
     * It replaces a token one. According to the offset of the token is his
     * style, removes it and installs the new one.
     *
     * @param tok New token
     * @param rcor Rigth side offset correction
     */
    private void replaceTokenInText(Token tok, int rcor) {
        ForegroundColorSpan[] fcs = getEditableText().getSpans(tok.offset - 1, tok.offset + tok.length - 1, ForegroundColorSpan.class);
        RelativeSizeSpan[] rcs = getEditableText().getSpans(tok.offset - 1, tok.offset + tok.length - 1, RelativeSizeSpan.class);
        TypefaceSpan[] tcs = getEditableText().getSpans(tok.offset - 1, tok.offset + tok.length - 1, TypefaceSpan.class);
        StyleSpan[] scs = getEditableText().getSpans(tok.offset - 1, tok.offset + tok.length - 1, StyleSpan.class);
        UnderlineSpan[] ucs = getEditableText().getSpans(tok.offset - 1, tok.offset + tok.length - 1, UnderlineSpan.class);

        try {
            for (ForegroundColorSpan fc : fcs) {
                getEditableText().removeSpan(fc);
            }
            for (RelativeSizeSpan rc : rcs) {
                getEditableText().removeSpan(rc);
            }
            for (TypefaceSpan tc : tcs) {
                getEditableText().removeSpan(tc);
            }
            for (StyleSpan sc : scs) {
                getEditableText().removeSpan(sc);
            }
            for (UnderlineSpan uc : ucs) {
                getEditableText().removeSpan(uc);
            }
        } catch (Exception ignored) {}
        getEditableText().setSpan(new ForegroundColorSpan(Color.parseColor("#" + tok.fontStyle.color)), tok.offset - 1, tok.offset + tok.length - 1 + rcor, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        getEditableText().setSpan(new RelativeSizeSpan(tok.fontStyle.size), tok.offset - 1, tok.offset + tok.length - 1 + rcor, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        getEditableText().setSpan(new TypefaceSpan(tok.fontStyle.font), tok.offset - 1, tok.offset + tok.length - 1 + rcor, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (tok.fontStyle.bold)
            getEditableText().setSpan(new StyleSpan(Typeface.BOLD), tok.offset - 1, tok.offset + tok.length - 1 + rcor, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (tok.fontStyle.italic)
            getEditableText().setSpan(new StyleSpan(Typeface.ITALIC), tok.offset - 1, tok.offset + tok.length - 1 + rcor, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (tok.fontStyle.underline)
            getEditableText().setSpan(new UnderlineSpan(), tok.offset - 1, tok.offset + tok.length - 1 + rcor, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    }


    /**
     * Shifts displacement of tokens going for token specified in the argument
     * by some amount.
     *
     * @param pos Position of the starting token
     * @param inde Incremented or decremented value
     */
    private void resizeTokensOffset(Pos pos, int inde) {
        boolean first = true;
            for (int i = pos.line; i < tokla.size(); i++) {
                for (int j = 0; j < tokla.get(i).size(); j++) {
                    if (first) {
                        if (j >= pos.offset) {
                            first = false;
                        }
                    } else {
                        tokla.get(i).get(j).offset += inde;
                    }
                }
            }

    }

    /**
     * Processes change the cursor position.It causes some token on which the
     * course and sets the global variables and the current cursor position
     * of the token.
     *
     * @param selStart -
     * @param selEnd -
     */
    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);

        curpos = selStart;

        if (tokla != null) {
                currentpos = getSelectedTokenPos(selStart);
            if (currentpos != null) {
                np = getNextTokenPos(currentpos, 1);
                currenttok = tokla.get(currentpos.line).get(currentpos.offset);
                Log.d("TOKEN", currenttok.lexeme + String.valueOf(" POS=" + currentpos.line + ":" + currentpos.offset + ":" + currentpos.interoffset));
            } else {
                Log.d("MSG", "currentpos NULL");
            }
        }
    }

    /**
     * It removes from the current token one character. It produces the
     * following steps. By reducing the size of the current token unit.It
     * calls the displacement of going ahead tokens. Change the text of the
     * token, and in the lexical analyzer redefines its type. Then it calls
     * the replacement of identity token in the text. It has branching
     * situations when the token is a single symbol. In this case, the token
     * is removed and all the front running tokens are shifted by one.
     *
     */
    private void deleteOneChar() {
        Pos tokpos = getSelectedTokenPos(curpos);
        Token tok = tokla.get(tokpos.line).get(tokpos.offset);
        tok.length--;
        resizeTokensOffset(tokpos, -1);
        if (tok.lexeme.length() > 1) {
            if (tokpos.interoffset == 1) {
                tok.lexeme = tok.lexeme.substring(tokpos.interoffset, tok.lexeme.length());
            } else {
                tok.lexeme = tok.lexeme.substring(0, tokpos.interoffset - 1) + tok.lexeme.substring(tokpos.interoffset, tok.lexeme.length());
            }
            try {
                tok = lexer.overrideToken(tok);
                replaceTokenInText(tok, 0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            //resizeTokensOffset(tokpos, -1);
            tokla.get(tokpos.line).remove(tokpos.offset);
            if (!currenttok.lexeme.equals("\n")) {
                if (tokla.get(0).size() != 0) {
                    joinTokens(new Pos(currentpos.line, currentpos.offset - 1, 0), new Pos(currentpos.line, currentpos.offset, 0));
                } else {
                    tokla.remove(0);
                }
            }
        }

    }


    //Метод режет рокен на две части и в середину вставляет новый
    private void insertToken(Pos pos, Token token) {
        try {
            Token ntok = new Token(currenttok.lexeme.substring(currentpos.interoffset, currenttok.length), currentpos.line, token.offset, currenttok.constflag);
            currenttok.lexeme = currenttok.lexeme.substring(0, currentpos.interoffset);
            currenttok.length = currentpos.interoffset;
            ntok = lexer.overrideToken(ntok);
            tokla.get(pos.line).set(pos.offset, lexer.overrideToken(currenttok));
            tokla.get(pos.line).add(pos.offset + 1, token);
            tokla.get(pos.line).add(pos.offset + 2, ntok);
            resizeTokensOffset(getNextTokenPos(pos, 1), token.length);
            replaceTokenInText(tokla.get(pos.line).get(pos.offset), 0);
            replaceTokenInText(token, 0);
            replaceTokenInText(ntok, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void joinTokens(Pos p1, Pos p2) {
        try {
            Token t2 = tokla.get(p2.line).get(p2.offset);
            if (t2.tag == Tag.NUM || t2.lexeme.equals("(") || t2.lexeme.equals(")") ||
                    t2.lexeme.equals("[") || t2.lexeme.equals("]") ||
                    t2.lexeme.equals("{") || t2.lexeme.equals("}") ||
                    t2.lexeme.equals("\"")|| t2.lexeme.equals("'") ||
                    t2.lexeme.equals("=") || (t2.lexeme.equals("!") ||
                    t2.lexeme.equals(">") || (t2.lexeme.equals("<")))){
                return;
            }
            tokla.get(p1.line).get(p1.offset).lexeme += t2.lexeme;
            tokla.get(p1.line).get(p1.offset).length += t2.length;
            tokla.get(p1.line).get(p1.offset).constflag = t2.constflag;
            tokla.get(p2.line).remove(p2.offset);
            Token tokn = lexer.overrideToken(tokla.get(p1.line).get(p1.offset));
            tokla.get(p1.line).set(p1.offset, tokn);
            replaceTokenInText(tokn , 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createToken(Pos pos, Token token) {
        try {
            if (np == null) {
                tokla.get(pos.line).add(token);
            } else {
                tokla.get(pos.line).add(pos.offset, token);
            }
            token.constflag = constf;
            Token tokn = lexer.overrideToken(token);
            resizeTokensOffset(getNextTokenPos(pos, 1), tokn.length);
            replaceTokenInText(tokn, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Pos getNextTokenPos(Pos pos, int step) {
            int ls = tokla.get(pos.line).size();
            if (pos.offset + step > ls) {
                int min = pos.offset + step - ls;
                return new Pos(pos.line + 1, min, 0);
            } else {
                if (ls < pos.offset + step + 1) {
                    return null;
                } else {
                    return new Pos(pos.line, pos.offset + step, 0);
                }
            }
    }

    
}