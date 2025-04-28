plugins {
    id("fabric-loom") version "1.7-SNAPSHOT" apply false
    id("com.replaymod.preprocess") version "c2041a3"
}

subprojects {
    repositories {
        //mavenLocal()
        mavenCentral()
        maven("https://libraries.minecraft.net/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://github.com/jitsi/jitsi-maven-repository/raw/master/releases/")
        maven("https://maven.fabricmc.net/")
        maven("https://jitpack.io")
    }
}

preprocess {
    val mc12101 = createNode("1.21.1", 12101, "yarn")
    val mc12100 = createNode("1.21", 12100, "yarn")
    val mc12006 = createNode("1.20.6", 12006, "yarn")
    val mc12005 = createNode("1.20.5", 12005, "yarn")
    val mc12004 = createNode("1.20.4", 12004, "yarn")
    val mc12002 = createNode("1.20.2", 12002, "yarn")
    val mc12001 = createNode("1.20.1", 12001, "yarn")
    val mc11904 = createNode("1.19.4", 11904, "yarn")
    val mc11802 = createNode("1.18.2", 11802, "yarn")
    val mc11800 = createNode("1.18", 11800, "yarn")
    val mc11701 = createNode("1.17.1", 11701, "yarn")
    val mc11605 = createNode("1.16.5", 11605, "yarn")

    mc12101.link(mc12100)
    mc12100.link(mc12006)
    mc12006.link(mc12005)
    mc12005.link(mc12004)
    mc12004.link(mc12002)
    mc12002.link(mc12001)
    mc12001.link(mc11904)
    mc11904.link(mc11802, file("versions/mapping-1.19.4-1.18.2.txt"))
    mc11802.link(mc11800)
    mc11800.link(mc11701, file("versions/mapping-1.18.2-1.17.1.txt"))
    mc11701.link(mc11605, file("versions/mapping-1.17.1-1.16.5.txt"))
}