package me.hektortm.woSSystems.utils;

public enum Letters_bg {
    A("\uE500"),
    B("\uE501"),
    C("\uE502"),
    D("\uE503"),
    E("\uE504"),
    F("\uE505"),
    G("\uE506"),
    H("\uE507"),
    I("\uE508"),
    J("\uE509"),
    K("\uE510"),
    L("\uE511"),
    M("\uE512"),
    N("\uE513"),
    O("\uE514"),
    P("\uE515"),
    Q("\uE516"),
    R("\uE517"),
    S("\uE518"),
    T("\uE519"),
    U("\uE520"),
    V("\uE521"),
    W("\uE522"),
    X("\uE523"),
    Y("\uE524"),
    Z("\uE525"),

    a("\uE526"),
    b("\uE527"),
    c("\uE528"),
    d("\uE529"),
    e("\uE530"),
    f("\uE531"),
    g("\uE532"),
    h("\uE533"),
    i("\uE534"),
    j("\uE535"),
    k("\uE536"),
    l("\uE537"),
    m("\uE538"),
    n("\uE539"),
    o("\uE540"),
    p("\uE541"),
    q("\uE542"),
    r("\uE543"),
    s("\uE544"),
    t("\uE545"),
    u("\uE546"),
    v("\uE547"),
    w("\uE548"),
    x("\uE549"),
    y("\uE550"),
    z("\uE551"),
    ZERO("\uE552"),
    ONE("\uE553"),
    TWO("\uE554"),
    THREE("\uE555"),
    FOUR("\uE556"),
    FIVE("\uE557"),
    SIX("\uE558"),
    SEVEN("\uE559"),
    EIGHT("\uE560"),
    NINE("\uE561"),

    SPACE("\uE562"),
    BORDER_LEFT("\uE563"),
    BORDER_RIGHT("\uE564"),

    COLON("\uE565"),
    COMMA("\uE566"),
    PIPE("\uE912"),

    CLOCK("\uE567"),
    CALENDER("\uE568"),

    NEGATIVE_SPACE("\uF001");


    Letters_bg(String letter) {
        this.letter = letter;
    };

    private final String letter;

    public String getLetter() {
        return letter;
    }
}
