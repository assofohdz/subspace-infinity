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
    Random 0
}

section('Burst') {
    BurstDamageLevel 250
}

section('Toggle') {
    AntiWarpPixels 1500
}

section('Prize') {
    MultiPrizeCount 10
    PrizeFactor 1300
    PrizeDelay 300
    PrizeHideCount 10
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

section('Team') {
    MaxFrequency 9999
    MaxPerTeam 6
    MaxPerPrivateTeam 0
    ForceEvenTeams 0
    DesiredTeams 2
    SpectatorFrequency 8025
}

section('Kill') {
    MaxBonus 0
    MaxPenalty 0
    RewardBase 0
    BountyIncreaseForKill 6
    EnterDelay 200
    KillPointsPerFlag 0
    KillPointsMinimumBounty 50
    DebtKills 0
    NoRewardKillDelay 0
    BountyRewardPercent 0
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
    BrickTime 12000
    BrickSpan 20
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
    AllowSavedShips 0
    SafetyLimit 90000
    FrequencyShift 900
    TickerDelay 1000
    ExtraPositionData 0
    WarpRadiusLimit 20
    ActivateAppShutdownTime 0
    NearDeathLevel 0
    VictoryMusic 0
    FrequencyShipTypes 0
    BannerPoints 0
    MaxLossesToPlay 0
    SpectatorQuiet 0
    TimedGame 0
    SheepMessage 'Baaah'
    ResetScoreOnFrequencyChange 0
    MaxPlaying 0
}

section('Territory') {
    RewardDelay 60000
    RewardBaseFlags 8
    RewardMinimumPlayers 5
    RewardPoints 30
}

section('Periodic') {
    RewardDelay 0
    RewardMinimumPlayers 2
    RewardPoints 50
}

section('Security') {
    S2CKickOutPercentWeapons 700
    SecurityKickOff 0
    SuicideLimit 10
    MaxShipTypeSwitchCount 40
    PacketModificationMax 3
    MaxDeathWithoutFiring 5
}

section('PacketLoss') {
    C2SKickOutPercent 750
    S2CKickOutPercent 750
    SpectatorPercentAdjust 150
    PacketLossDisableWeapons 1
    C2SNegativeKickOutPercent 50
}

section('Routing') {
    RadarFavor 3
    CloseEnoughBulletAdjust 512
    CloseEnoughBombAdjust 2048
    DeathDistance 16384
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
    BulletDamageLevel 100
    BulletDamageUpgrade 50
    BulletAliveTime 550
    ExactDamage 1
}
