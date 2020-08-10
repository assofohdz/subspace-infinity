# Build Stats:

[![Build Status](https://travis-ci.org/assofohdz/Subspace-Infinity.svg?branch=master)](https://travis-ci.org/assofohdz/Subspace-Infinity)

# Pre-requisites to build

See also CONTRIBUTE

1. Create a local folder to contain all dependency repos
2. Open a powershell in that folder

Git clone to local repos
1. Run "git clone https://github.com/jMonkeyEngine-Contributions/Lemur.git"
2. Run "git clone https://github.com/Simsilica/SiO2.git"
3. Run "git clone https://github.com/Simsilica/SimEthereal.git"
4. Run "git clone https://github.com/Simsilica/SimMath.git"
5. Run "git clone https://github.com/Simsilica/Pager.git"
6. Run "git clone https://github.com/assofohdz/moss.git"
7. Run "git clone https://github.com/jMonkeyEngine-Contributions/zay-es.git"
8. Run "git clone https://github.com/Simsilica/SimFX.git"

Gradle build and install the following dependency libraries: Run "gradle install" for the following local clones
1. Lemur + Lemur-Proto + Lemur-Props
2. SiO2
3. SimEthereal
4. SimMath
5. Pager
6. Zay-ES + Zay-ES-Net
7. Moss/mphys
8. Moss/mblock
9. Moss/mblock-physb
10. Moss/sio2-mblock
11. Moss/sio2-mphys
12. Moss/mworld

Clone Subspace Infinity
1. Run "git clone https://github.com/assofohdz/Subspace-Infinity.git"
2. Go to the local repo folder and run "gradle build"
