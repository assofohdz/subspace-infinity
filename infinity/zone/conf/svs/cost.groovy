// Standard VIE [Cost] section — canonical baseline copied verbatim from
// SubspaceServer's conf/svs/cost. All zero today (purchases disabled in the
// canonical SVS preset). Do not edit in place — derive a variant under
// /conf/svs-<name>/ if you need different cost tuning.
//
// First Groovy fragment (smoke test for the Phase A loader infra). The
// matching INI file at /conf/svs/cost stays in place for now — this is a
// parallel port that exercises GroovyFragmentLoader; the still-INI
// /conf/svs/svs.conf composite continues to #include the INI version. Once
// the rest of the svs/ preset migrates and svs/svs.conf retires, the INI
// /conf/svs/cost will retire alongside it.

section('Cost') {
    PurchaseAnytime 0
    XRadar          0
    Recharge        0
    Energy          0
    Rotation        0
    Stealth         0
    Cloak           0
    Gun             0
    Bomb            0
    Bounce          0
    Thrust          0
    Speed           0
    MultiFire       0
    Prox            0
    Super           0
    Shield          0
    Shrap           0
    AntiWarp        0
    Repel           0
    Burst           0
    Decoy           0
    Thor            0
    Brick           0
    Rocket          0
    Portal          0
}
