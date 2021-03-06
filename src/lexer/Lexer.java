package lexer;

import misc.Pair;

/*
    DONE: Keywords:
    var
    type
    is
    end
    integer
    real
    boolean
    true
    false
    record
    array
    while
    loop
    for
    in
    reverse
    if
    then
    else
    routine
    and
    or
    not
    xor
    print
    return

    Symbolic Tokens:
    DONE: .. - range
    DONE: + - addition, can be unary to indicate the value is > 0
    DONE: - - subtraction, can be unary to mean negation
    DONE: * - multiplication
    DONE: / - division
    DONE: % - remainder from division
    DONE: [ -
    DONE: ] - addressing an array element
    DONE: < - less than
    DONE: <= - less or equal to
    DONE: > - greater than
    DONE: >= - greater or equal to
    DONE: = - equal to
    DONE: /= - not equal to
    DONE: . - addressing a record attribute
    DONE: , - separates parameters in routine calls/declarations
    DONE: := - assignment
    DONE: : - used to specify type in declarations
    DONE: ( -
    DONE: ) - used to enclose parameter list
    DONE: // - single-line comment
    DONE: /* - enclose multiline comments
    DONE: ; - statement separator

    Complex Tokens:
    DONE: Identifier
    DONE: IntegralLiteral
    DONE: RealLiteral
 */


public class Lexer {
    private StringReaderWithPosition in;
    private CharacterBuffer buffer;
    private int c;
    private Token enqueuedToken = null;


    public Lexer() {
    }

    public void tokenize(String sourceText) {
        this.in = new StringReaderWithPosition(sourceText);
        this.c = in.read();
        this.buffer = new CharacterBuffer();
    }

    public Token lex() {
        while (c != -1 || enqueuedToken != null) {
            if (enqueuedToken != null) {
                Token tok = enqueuedToken;
                enqueuedToken = null;
                return tok;
            }
            if (Character.isLetter(c) || c == '_') {
                return scanKeywordOrIdentifier();
            } else if (Character.isDigit(c)) {
                return scanRealOrIntegerLiteral();
            } else if (c == '.') {
                return scanAmbiguousWithDot();
            } else if (c == '<' || c == '>' || c == ':') {
                return scanAmbiguousWithEquals();
            } else if (c == '/') {
                return scanAmbiguousWithSlash();
            } else if (c == '(' || c == ')' || c == '[' || c == ']' ||
                    c == '+' || c == '-' || c == '*' || c == '%' ||
                    c == '=' || c == ',' || c == ';') {
                return scanSingleCharacterToken();
            } else if (c == '\n') {
                return scanNewlineSeparator();
            } else if (Character.isWhitespace(c)) {
                // skip it
                c = in.read();
            } else if (c != -1) {
                return scanIllegalCharacter();
            }
        }
        return new Token("", TokenType.EOF, new Pair<>(in.line(), in.pos()));
    }


    /**
     * this method handles:
     * 1. identifiers
     * 2. keywords
     *
     * @return a new lexer.Token object of one of the listed types
     */
    private Token scanKeywordOrIdentifier() {
        Token tok;
        Pair<Integer, Integer> pos = new Pair<>(in.line(), in.pos());
        while ((Character.isLetterOrDigit(c) || c == '_') && c != -1) {
            buffer.add(c);
            c = in.read();
        }

        String st = buffer.toString();
        TokenType type;
        buffer.flush();

        if ((type = Token.KEYWORD_TABLE.get(st)) != null) {
            tok = new Token(st, type, pos);
        } else {
            tok = new Token(st, TokenType.IDENTIFIER, pos);
        }
        return tok;
    }

    /**
     * this method handles:
     * 1. real literals that start with a digit (e.g. 0.12132)
     * 2. integer literals (zeros at the beginning are allowed)
     * 3. real literals followed by '..' operator
     *
     * @return a new lexer.Token object of one of the listed types
     */
    private Token scanRealOrIntegerLiteral() {
        Token tok;
        Pair<Integer, Integer> pos = new Pair<>(in.line(), in.pos());
        while (Character.isDigit(c) && c != '.' && c != -1) {
            buffer.add(c);
            c = in.read();
        }

        if (c == '.') {
            int nextChar = in.read();
            if (nextChar == '.') { // which means '..' operator was encountered
                tok = new Token(buffer.toString(),
                        TokenType.INTEGER, pos);
                enqueuedToken = new Token("..", TokenType.RANGE, pos);
                buffer.flush();
                c = in.read(); // reading in the next unprocessed character
            } else if (Character.isDigit(nextChar)) { // which means a real literal was encountered
                buffer.add(c);
                while (Character.isDigit(nextChar)) {
                    buffer.add(nextChar);
                    nextChar = in.read();
                }
                tok = new Token(buffer.toString(), TokenType.REAL_LITERAL, pos);
                buffer.flush();
                c = nextChar;

            } else { // which means a real literal without digits after dot (like 1. <=> 1.0)

                buffer.add(c);
                tok = new Token(buffer.toString(), TokenType.REAL_LITERAL, pos);
                buffer.flush();
                c = nextChar;

            }
        } else {
            tok = new Token(buffer.toString(), TokenType.INTEGER_LITERAL, pos);
            buffer.flush();
        }
        return tok;
    }

    /**
     * this method handles:
     * 1. real literals starting with '.' (e.g. '.12231')
     * 2. '..' operator
     * 3. '.' operator
     *
     * @return a new lexer.Token object of one of the listed types
     */
    private Token scanAmbiguousWithDot() {
        Token tok;
        Pair<Integer, Integer> pos = new Pair<>(in.line(), in.pos());
        buffer.add(c);
        int nextChar = in.read();

        // '..' operator
        if (nextChar == '.') {
            buffer.add(nextChar);
            tok = new Token(buffer.toString(), TokenType.RANGE, pos);
            buffer.flush();
            c = in.read(); // in this case nextChar is already processed,
            // therefore c gets the value of the next character
        }

        // real literals starting with '.' (e.g. '.12231')
        else if (Character.isDigit(nextChar)) { // .001234125 literals
            while (Character.isDigit(nextChar) && nextChar != -1) {
                buffer.add(nextChar);
                nextChar = in.read();
            }
            tok = new Token(buffer.toString(), TokenType.REAL_LITERAL, pos);
            buffer.flush();
            c = nextChar;
        }
        // standalone '.'
        else {
            tok = new Token(buffer.toString(), TokenType.DOT, pos);
            buffer.flush();
            c = nextChar;
        }
        return tok;
    }

    /**
     * this method handles operators:
     * 1. '<'
     * 2. '>'
     * 3. ':'
     * 4. '>='
     * 5. '<='
     * 6. ':='
     *
     * @return a new lexer.Token object of one of the listed types
     */
    private Token scanAmbiguousWithEquals() {
        Pair<Integer, Integer> pos = new Pair<>(in.line(), in.pos());
        buffer.add(c);
        int nextChar = in.read();
        TokenType type;

        // operators '>=', '<=' and ':='
        if (nextChar == '=') {
            buffer.add(nextChar);
            switch (c) {
                case '>' -> type = TokenType.GEQUALS;
                case '<' -> type = TokenType.LEQUALS;
                case ':' -> type = TokenType.ASSIGN;
                default -> type = null;
            }
            c = in.read();
        }
        // operators '>', '<', ':'
        else {
            switch (c) {
                case '>' -> type = TokenType.GREATER;
                case '<' -> type = TokenType.LESS;
                case ':' -> type = TokenType.COLON;
                default -> type = null;
            }
            c = nextChar;
        }
        Token tok = new Token(buffer.toString(), type, pos);
        buffer.flush();
        return tok;
    }

    /**
     * this method handles:
     * 1. multiline comments /* ... ,
     * 2. single line comments // ... '\n'
     * 3. operator '/'
     * 4. operator '/='
     *
     * @return a new lexer.Token object of one of the listed types
     */
    private Token scanAmbiguousWithSlash() {
        Token tok;
        Pair<Integer, Integer> pos = new Pair<>(in.line(), in.pos());
        buffer.add(c);
        int nextChar = in.read();

        // operator '/='
        if (nextChar == '=') {
            buffer.add(nextChar);
            tok = new Token(buffer.toString(), TokenType.NEQUALS, pos);
            buffer.flush();
            c = in.read();
        }

        // multiline comments
        else if (nextChar == '*') {
            buffer.add(nextChar);
            tok = new Token(buffer.toString(), TokenType.MLCOMMENT_START, pos);
            buffer.flush();
            while (((c = in.read()) != '*' || (nextChar = in.read()) != '/') &&
                    nextChar != -1 && c != -1) {
                // do nothing (for now)
                // we can potentially do something
                // with comment text in this loop
            }
            if (c == '*' && nextChar == '/') {
                buffer.add(c);
                buffer.add(nextChar);
                enqueuedToken = new Token(buffer.toString(), TokenType.MLCOMMENT_END, pos);
            }
            buffer.flush();
            c = in.read();
        }

        // single line comments
        else if (nextChar == '/') {
            buffer.add(nextChar);
            tok = new Token(buffer.toString(), TokenType.SLCOMMENT, pos);
            buffer.flush();
            while ((c = in.read()) != '\n' && c != -1) {
                // do nothing (for now)
                // we can potentially do something
                // with comment text in this loop
            }
            // when the '\n' or eof is reached, proceed further
            if (c == '\n') {
                enqueuedToken = new Token("\n", TokenType.SEPARATOR, pos);
            }
            c = in.read();
        }
        // operator '/'
        else {
            tok = new Token(buffer.toString(), TokenType.DIVIDE, pos);
            buffer.flush();
            c = nextChar;
        }
        return tok;
    }

    /**
     * this clause handles:
     * single-character tokens, such as:
     * '(', ')', '[', ']', '+', '-', '*', '%', '=', ',', ';'
     *
     * @return a new lexer.Token object of one of the listed types
     */
    private Token scanSingleCharacterToken() {
        TokenType type;
        Pair<Integer, Integer> pos = new Pair<>(in.line(), in.pos());
        switch (c) {
            case '(' -> type = TokenType.LPAREN;
            case ')' -> type = TokenType.RPAREN;
            case '[' -> type = TokenType.LBRACKET;
            case ']' -> type = TokenType.RBRACKET;
            case '+' -> type = TokenType.ADD;
            case '-' -> type = TokenType.MINUS;
            case '*' -> type = TokenType.MULTIPLY;
            case '%' -> type = TokenType.REMAINDER;
            case '=' -> type = TokenType.EQUALS;
            case ',' -> type = TokenType.COMMA;
            case ';' -> type = TokenType.SEPARATOR;
            default -> type = null;
        }

        buffer.add(c);
        Token tok = new Token(buffer.toString(), type, pos);
        buffer.flush();
        c = in.read();
        return tok;
    }

    /**
     * this method handles:
     * 1. newline separator
     *
     * @return a new lexer.Token object of one of the listed types
     */
    private Token scanNewlineSeparator() {
        Pair<Integer, Integer> pos = new Pair<>(in.line(), in.pos());
        c = in.read();
        return new Token("\n", TokenType.SEPARATOR, pos);
    }

    /**
     * this method handles:
     * 1. Illegal Characters
     *
     * @return a new lexer.Token object of one of the listed types
     */
    private Token scanIllegalCharacter() {
        buffer.add(c);
        Token tok = new Token(buffer.toString(), TokenType.ILLEGAL, new Pair<>(in.line(), in.pos()));
        buffer.flush();
        c = in.read();
        return tok;
    }


}


