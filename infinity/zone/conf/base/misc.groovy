section('Bullet') {
    BulletDamageLevel 200
    BulletDamageUpgrade 100
    BulletAliveTime 550
}

section('Bomb') {
    BombDamageLevel 750
    BombAliveTime 6000
    BombExplodeDelay 150
    BombExplodePixels 80
    ProximityDistance 3
    JitterTime 72
    BombSafety 1
    EBombShutdownTime 400
    EBombDamagePercent 1000
    BBombDamagePercent 1000
}

section('Mine') {
    MineAliveTime 12000
    TeamMaxMines 12
}

section('Shrapnel') {
    ShrapnelSpeed 3000
    InactiveShrapDamage 3
    ShrapnelDamagePercent 1000
    Random 1
}

section('Burst') {
    BurstDamageLevel 515
}

section('Toggle') {
    AntiWarpPixels 1500
}

section('Prize') {
    MultiPrizeCount 10
    PrizeFactor 1000
    PrizeDelay 300
    PrizeHideCount 30
    MinimumVirtual 256
    UpgradeVirtual 6
    PrizeMaxExist 8000
    PrizeMinExist 4000
    PrizeNegativeFactor 300
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
    RepelSpeed 5000
    RepelTime 225
    RepelDistance 512
}

section('Message') {
    MessageReliable 0
    AllowAudioMessages 1
    BongAllowed 0
    QuickMessageLimit 8
    MessageTeamReliable 1
}

section('Wormhole') {
    GravityBombs 1
    SwitchTime 0
}

section('Brick') {
    BrickTime 12000
    BrickSpan 7
}

section('Rocket') {
    RocketThrust 25
    RocketSpeed 5500
}

section('Door') {
    DoorDelay 400
    DoorMode(-1)
}

section('Misc') {
    WarpPointDelay 6000
    DecoyAliveTime 3000
    BounceFactor 22
    SendPositionDelay 10
    SlowFrameCheck 0
    AllowSavedShips 1
    SafetyLimit 90000
    FrequencyShift 900
    TickerDelay 1000
    ExtraPositionData 0
    WarpRadiusLimit 1024
    ActivateAppShutdownTime 1500
    NearDeathLevel 0
    VictoryMusic 1
    FrequencyShipTypes 0
    BannerPoints 5000
    MaxLossesToPlay 0
    SpectatorQuiet 0
    TimedGame 0
    SheepMessage 'Sheep successfully cloned -- hello Dolly'
    ResetScoreOnFrequencyChange 0
    MaxPlaying 0
}

section('Custom') {
    SaveStatsTime 720000
}

section('Owner') {
    UserId(-1)
    Name 'None'
}
