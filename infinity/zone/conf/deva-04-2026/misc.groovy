section('Spectator') {
    HideFlags 0
    NoXRadar 0
}

section('Bullet') {
    ExactDamage 1
    BulletDamageLevel 175
    BulletDamageUpgrade 150
    BulletAliveTime 65
}

section('Misc') {
    SlowFrameRate 0
    MaxTimerDrift 1
    DisableScreenshot 0
    WarpPointDelay 12000
    DecoyAliveTime 4500
    SafetyLimit 0
    FrequencyShift 643
    NearDeathLevel 0
    ActivateAppShutdownTime 0
    SendPositionDelay 1
    BounceFactor 26
    TickerDelay 32000
    WarpRadiusLimit 1
    ExtraPositionData 0
    SlowFrameCheck 0
    AllowSavedShips 0
    VictoryMusic 1
    AntiWarpSettleDelay 11
}

section('Spawn') {
    "Team0-Radius"(10)
    "Team0-X"(-512)
    "Team0-Y"(509)
    "Team1-Radius"(10)
    "Team1-X"(-512)
    "Team1-Y"(509)
    "Team2-Radius"(10)
    "Team2-X"(-512)
    "Team2-Y"(509)
    "Team3-Radius"(10)
    "Team3-X"(-512)
    "Team3-Y"(509)
}

section('Bomb') {
    BombDamageLevel 5600
    BombAliveTime 250
    BombExplodeDelay 30
    BombExplodePixels 18
    JitterTime 70
    ProximityDistance 1
    EBombShutdownTime 6000
    EBombDamagePercent 1000
    BBombDamagePercent 1000
    BombSafety 1
}

section('Mine') {
    MineAliveTime 6000
    TeamMaxMines 21
}

section('Shrapnel') {
    InactiveShrapDamage 65
    ShrapnelSpeed 3000
    Random 1
    ShrapnelDamagePercent 450
}

section('Burst') {
    BurstDamageLevel 200
}

section('Toggle') {
    AntiWarpPixels 1500
}

section('Prize') {
    DeathPrizeTime 5000
    EngineShutdownTime 300
    PrizeFactor 6000
    PrizeDelay 2000
    MinimumVirtual 85
    UpgradeVirtual 1
    PrizeMaxExist 5000
    PrizeMinExist 300
    PrizeNegativeFactor 10000
    MultiPrizeCount 10
    TakePrizeReliable 1
    PrizeHideCount 90
}

section('Radar') {
    MapZoomFactor 8
    RadarNeutralSize 100
    RadarMode 0
}

section('Repel') {
    RepelSpeed 4700
    RepelTime 280
    RepelDistance 330
}

section('Message') {
    MessageReliable 0
    AllowAudioMessages 0
}

section('Wormhole') {
    SwitchTime 1000
    GravityBombs 1
}

section('Brick') {
    BrickTime 1000
}

section('Rocket') {
    RocketThrust 85
    RocketSpeed 4000
}

section('Door') {
    DoorDelay 0
    DoorMode 255
}
