section('Bomb') {
    BombDamageLevel 750
    BombAliveTime 3000
    BombExplodeDelay 150
    BombExplodePixels 80
    ProximityDistance 3
    JitterTime 72
    BombSafety 0
    EBombShutdownTime 500
    EBombDamagePercent 600
    BBombDamagePercent 1000
}

section('Mine') {
    MineAliveTime 12000
    TeamMaxMines 15
}

section('Shrapnel') {
    ShrapnelSpeed 3000
    InactiveShrapDamage 3
    ShrapnelDamagePercent 1000
    Random 0
}

section('Burst') {
    BurstDamageLevel 300
}

section('Toggle') {
    AntiWarpPixels 1500
}

section('Prize') {
    MultiPrizeCount 19
    PrizeFactor 4000
    PrizeDelay 300
    PrizeHideCount 20
    MinimumVirtual 256
    UpgradeVirtual 9
    PrizeMaxExist 8000
    PrizeMinExist 4000
    PrizeNegativeFactor 300
    DeathPrizeTime 1000
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
    MaxFrequency 9999
    MaxPerTeam 10
    MaxPerPrivateTeam 0
    ForceEvenTeams 0
    DesiredTeams 2
    SpectatorFrequency 8025
}

section('Kill') {
    MaxBonus 0
    MaxPenalty 0
    RewardBase 0
    BountyIncreaseForKill 20
    EnterDelay 200
    KillPointsPerFlag 0
    KillPointsMinimumBounty 150
    DebtKills 0
    NoRewardKillDelay 0
    BountyRewardPercent 0
    FixedKillReward(-1)
    JackpotBountyPercent 0
}

section('Repel') {
    RepelSpeed 4000
    RepelTime 250
    RepelDistance 520
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
    SwitchTime 1000
}

section('Latency') {
    SendRoutePercent 500
    ClientSlowPacketTime 50
    S2CNoDataKickoutDelay 500
    KickOutDelay 1000
    NoFlagDelay 300
    NoFlagPenalty 1000
    SlowPacketKickoutPercent 200
    ClientSlowPacketKickoutPercent 500
    ClientSlowPacketSampleSize 100
    SlowPacketTime 50
    SlowPacketSampleSize 300
    MaxLatencyForWeapons 45
    MaxLatencyForPrizes 80
    MaxLatencyForKickOut 120
    LatencyKickOutTime 2200
    CutbackWatermark 2400
}

section('Brick') {
    BrickTime 3000
    BrickSpan 10
}

section('Rocket') {
    RocketThrust 25
    RocketSpeed 5000
}

section('Door') {
    DoorDelay 500
    DoorMode(-1)
}

section('Misc') {
    WarpPointDelay 12000
    DecoyAliveTime 3000
    BounceFactor 22
    SendPositionDelay 10
    SlowFrameCheck 0
    AllowSavedShips 1
    SafetyLimit 90000
    FrequencyShift 900
    TickerDelay 1000
    ExtraPositionData 0
    WarpRadiusLimit 200
    ActivateAppShutdownTime 1000
    NearDeathLevel 300
    VictoryMusic 0
    FrequencyShipTypes 0
    BannerPoints 20000
    MaxLossesToPlay 0
    SpectatorQuiet 0
    TimedGame 0
    SheepMessage 'Back by Popular Demand!!!'
    ResetScoreOnFrequencyChange 0
    MaxPlaying 0
}

section('Territory') {
    RewardDelay 60000
    RewardBaseFlags 200
    RewardMinimumPlayers 250
    RewardPoints 0
}

section('Periodic') {
    RewardDelay 30000
    RewardMinimumPlayers 4
    RewardPoints(-1)
}

section('Security') {
    S2CKickOutPercentWeapons 700
    SecurityKickOff 1
    SuicideLimit 10
    MaxShipTypeSwitchCount 40
    PacketModificationMax 3
    MaxDeathWithoutFiring 5
}

section('PacketLoss') {
    C2SKickOutPercent 850
    S2CKickOutPercent 850
    SpectatorPercentAdjust 200
    PacketLossDisableWeapons 1
    C2SNegativeKickOutPercent 50
}

section('Routing') {
    RadarFavor 3
    CloseEnoughBulletAdjust 512
    CloseEnoughBombAdjust 2048
    DeathDistance 2800
    DoubleSendPercent 800
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
    BulletDamageLevel 200
    BulletDamageUpgrade 100
    BulletAliveTime 450
    ExactDamage 0
}
