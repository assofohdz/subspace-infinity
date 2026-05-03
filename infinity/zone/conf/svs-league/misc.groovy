section('Bomb') {
    BombDamageLevel 750
    BombAliveTime 6000
    BombExplodeDelay 150
    BombExplodePixels 80
    ProximityDistance 3
    JitterTime 72
    BombSafety 0
    EBombShutdownTime 400
    EBombDamagePercent 1000
    BBombDamagePercent 1000
}

section('Mine') {
    MineAliveTime 6000
    TeamMaxMines 12
}

section('Shrapnel') {
    ShrapnelSpeed 3000
    InactiveShrapDamage 5
    ShrapnelDamagePercent 900
    Random 0
}

section('Burst') {
    BurstDamageLevel 212
}

section('Toggle') {
    AntiWarpPixels 1500
}

section('Prize') {
    MultiPrizeCount 10
    PrizeFactor 0
    PrizeDelay 700
    PrizeHideCount 10
    MinimumVirtual 10
    UpgradeVirtual 6
    PrizeMaxExist 8000
    PrizeMinExist 4000
    PrizeNegativeFactor 32000
    DeathPrizeTime 800
    EngineShutdownTime 700
    TakePrizeReliable 0
    S2CTakePrizeReliable 0
}

section('Radar') {
    RadarMode 0
    RadarNeutralSize 128
    MapZoomFactor 10
}

section('Repel') {
    RepelSpeed 3600
    RepelTime 225
    RepelDistance 512
}

section('Message') {
    MessageReliable 0
    AllowAudioMessages 0
    BongAllowed 0
    QuickMessageLimit 8
    MessageTeamReliable 1
}

section('Wormhole') {
    GravityBombs 1
    SwitchTime 100000
}

section('Brick') {
    BrickTime 2000
    BrickSpan 15
}

section('Rocket') {
    RocketThrust 25
    RocketSpeed 4000
}

section('Door') {
    DoorDelay 500
    DoorMode(-1)
}

section('Misc') {
    WarpPointDelay 6000
    DecoyAliveTime 1000
    BounceFactor 22
    SendPositionDelay 10
    SlowFrameCheck 0
    AllowSavedShips 0
    SafetyLimit 90000
    FrequencyShift 900
    TickerDelay 1000
    ExtraPositionData 0
    WarpRadiusLimit 200
    ActivateAppShutdownTime 0
    NearDeathLevel 0
    VictoryMusic 0
    FrequencyShipTypes 0
    BannerPoints 0
    MaxLossesToPlay 0
    SpectatorQuiet 0
    TimedGame 0
    SheepMessage 'Subspace FOREVER!'
    ResetScoreOnFrequencyChange 0
    MaxPlaying 0
}
