// Default arena fallback — used when an arena is loaded by name but has no
// folder of its own. Read by GroovyArenaLoader.

arena {
    map '(default).lvl'
    includeFragment '/conf/base/prize-weights.groovy'
    includeFragment '/conf/base/ship-warbird.groovy'
    includeFragment '/conf/base/ship-javelin.groovy'
    includeFragment '/conf/base/ship-spider.groovy'
    includeFragment '/conf/base/ship-leviathan.groovy'
    includeFragment '/conf/base/ship-terrier.groovy'
    includeFragment '/conf/base/ship-weasel.groovy'
    includeFragment '/conf/base/ship-lancaster.groovy'
    includeFragment '/conf/base/misc.groovy'
    includeFragment '/conf/base/bullet.groovy'
    includeFragment '/conf/base/burst.groovy'
}
