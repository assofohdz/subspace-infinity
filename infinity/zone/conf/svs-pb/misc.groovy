section('Bomb') {
    BombDamageLevel 750
    BombAliveTime 3000
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
    MineAliveTime 3000
    TeamMaxMines 30
}

section('Shrapnel') {
    ShrapnelSpeed 3000
    InactiveShrapDamage 3
    ShrapnelDamagePercent 1000
    Random 0
}

section('Burst') {
    BurstDamageLevel 212
}

section('Toggle') {
    AntiWarpPixels 1500
}

section('Prize') {
    MultiPrizeCount 4
    PrizeFactor 4125
    PrizeDelay 300
    PrizeHideCount 100
    MinimumVirtual 50
    UpgradeVirtual 0
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

section('Team') {
    MaxFrequency 2
    MaxPerTeam 8
    MaxPerPrivateTeam 0
    ForceEvenTeams 0
    DesiredTeams 2
    SpectatorFrequency 8025
}

section('Kill') {
    MaxBonus 0
    MaxPenalty 0
    RewardBase 0
    BountyIncreaseForKill 10
    EnterDelay 200
    KillPointsPerFlag 2
    KillPointsMinimumBounty 80
    DebtKills 0
    NoRewardKillDelay 0
    BountyRewardPercent 10
    FixedKillReward(-1)
    JackpotBountyPercent 0
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
    MessageDistance 17000
}

section('Wormhole') {
    GravityBombs 1
    SwitchTime 0
}

section('Latency') {
    SendRoutePercent 500
    ClientSlowPacketSampleSize 100
    ClientSlowPacketTime 40
    S2CNoDataKickoutDelay 500
    KickOutDelay 1000
    NoFlagDelay 500
    NoFlagPenalty 300
    SlowPacketKickoutPercent 200
    ClientSlowPacketKickoutPercent 200
    SlowPacketTime 50
    SlowPacketSampleSize 300
    MaxLatencyForWeapons 45
    MaxLatencyForPrizes 80
    MaxLatencyForKickOut 120
    LatencyKickOutTime 2200
    CutbackWatermark 2400
}

section('Brick') {
    BrickTime 1500
    BrickSpan 6
}

section('Rocket') {
    RocketThrust 25
    RocketSpeed 5500
}

section('Door') {
    DoorDelay 312
    DoorMode(-1)
}

section('Misc') {
    WarpPointDelay 6000
    DecoyAliveTime 1500
    BounceFactor 22
    SendPositionDelay 10
    SlowFrameCheck 0
    AllowSavedShips 1
    SafetyLimit 90000
    FrequencyShift 900
    TickerDelay 1000
    ExtraPositionData 1
    WarpRadiusLimit 100
    ActivateAppShutdownTime 300
    NearDeathLevel 0
    VictoryMusic 0
    FrequencyShipTypes 0
    BannerPoints 0
    MaxLossesToPlay 0
    SpectatorQuiet 0
    TimedGame 0
    SheepMessage ''
    ResetScoreOnFrequencyChange 0
    MaxPlaying 0
}

section('Territory') {
    RewardDelay 30000
    RewardBaseFlags 8
    RewardMinimumPlayers 254
    RewardPoints 30
}

section('Periodic') {
    RewardDelay 0
    RewardMinimumPlayers 3
    RewardPoints 3
}

section('Security') {
    S2CKickOutPercentWeapons 700
    SuicideLimit 10
    MaxShipTypeSwitchCount 40
    PacketModificationMax 3
    MaxDeathWithoutFiring 5
    SecurityKickOff 0
}

section('PacketLoss') {
    C2SKickOutPercent 800
    S2CKickOutPercent 800
    SpectatorPercentAdjust 100
    PacketLossDisableWeapons 0
    C2SNegativeKickOutPercent 50
}

section('Routing') {
    RadarFavor 3
    CloseEnoughBulletAdjust 512
    CloseEnoughBombAdjust 2048
    DeathDistance 2800
    DoubleSendPercent 880
    WallResendCount 1
}

section('King') {
    DeathCount 0
    ExpireTime 30000
    RewardFactor 1000
    NonCrownAdjustTime 3000
    NonCrownMinimumBounty 100
    CrownRecoverKills 3
}

section('Bullet') {
    BulletDamageLevel 212
    BulletDamageUpgrade 0
    BulletAliveTime 250
    ExactDamage 1
}
