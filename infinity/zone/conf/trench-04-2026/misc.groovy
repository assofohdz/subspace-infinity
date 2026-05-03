section('Spectator') {
    HideFlags 0
    NoXRadar 1
}

section('Misc') {
    SlowFrameRate 0
    MaxTimerDrift 5
    DisableScreenshot 0
    WarpPointDelay 24000
    DecoyAliveTime 10000
    SafetyLimit 180000
    FrequencyShift 900
    NearDeathLevel 0
    ActivateAppShutdownTime 0
    SendPositionDelay 10
    BounceFactor 28
    TickerDelay 1000
    WarpRadiusLimit 83
    ExtraPositionData 0
    SlowFrameCheck 0
    AllowSavedShips 0
    VictoryMusic 0
    AntiWarpSettleDelay 0
}

section('Spawn') {
    "Team0-Radius"(96)
    "Team0-X"(416)
    "Team0-Y"(-480)
    "Team1-Radius"(96)
    "Team1-X"(-416)
    "Team1-Y"(-480)
    "Team2-Radius"(0)
    "Team2-X"(0)
    "Team2-Y"(0)
    "Team3-Radius"(0)
    "Team3-X"(0)
    "Team3-Y"(0)
}

section('Mine') {
    MineAliveTime 15000
    TeamMaxMines 12
}

section('Shrapnel') {
    InactiveShrapDamage 3
    ShrapnelSpeed 6000
    Random 0
    ShrapnelDamagePercent 1500
}

section('Burst') {
    BurstDamageLevel 2000
}

section('Toggle') {
    AntiWarpPixels 1250
}

section('Prize') {
    DeathPrizeTime 2000
    EngineShutdownTime 500
    PrizeFactor 400
    PrizeDelay 100
    MinimumVirtual 250
    UpgradeVirtual 0
    PrizeMaxExist 12000
    PrizeMinExist 4000
    PrizeNegativeFactor 300
    MultiPrizeCount 1000
    TakePrizeReliable 0
    PrizeHideCount 50
}

section('Radar') {
    MapZoomFactor 8
    RadarNeutralSize 128
    RadarMode 0
}

section('Repel') {
    RepelSpeed 1200
    RepelTime 150
    RepelDistance 150
}

section('Message') {
    MessageReliable 1
    AllowAudioMessages 1
}

section('Wormhole') {
    SwitchTime 0
    GravityBombs 1
}

section('Brick') {
    BrickTime 1000
}

section('Rocket') {
    RocketThrust 100
    RocketSpeed 3000
}

section('Door') {
    DoorDelay 250
    DoorMode 41
}
