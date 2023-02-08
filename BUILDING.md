# Building

These instructions assume you are using Windows 10. Java is cross-platform and Subspace-Infinity should be able to build and work on any platform, but it has only been developed and tested on Windows 10.

If you want to setup an IDE and be able to contribute to Subspace-Infinity, you should also follow the guide for [contributing](CONTRIBUTING.md).

## Building
1. Launch [PowerShell](https://docs.microsoft.com/en-us/PowerShell/scripting/windows-PowerShell/install/installing-windows-PowerShell?view=PowerShell-7) as Administrator, or use [Windows Terminal](https://www.microsoft.com/en-us/p/windows-terminal/9n0dx20hk701) run as Administrator.
1. Install [Chocolatey](https://chocolatey.org/install)
1. Install the following development tools with choco: `choco install git`
1. Install gradle: `choco install gradle --version=6.3`
2. Install java (AdoptOpenJDK moved to be named Temurin): `choco install temurin11`
3. Add/update JAVA_HOME system variable (eg. Variable = JAVA_HOME, Value = "C:\Program Files\AdoptOpenJDK\jdk-16.0.1.9-hotspot")
4. Add %JAVA_HOME%/bin to your Path system variable
5. Close your Administrator PowerShell and open a new PowerShell session
6. Create a folder to hold everything. We'll refer to this as the _Workspace_ folder. For this example, we will use "C:\workspace\Subspace" and setup an environment variable to hold the value:
    ``` shell
    $env:SubspaceWorkspace = "C:\workspace\Subspace"
    mkdir $env:SubspaceWorkspace
    mkdir $env:SubspaceWorkspace\Clipper
    mkdir $env:SubspaceWorkspace\jMonkeyEngine
    mkdir $env:SubspaceWorkspace\Simsilica
    mkdir $env:SubspaceWorkspace\pspeed42
    
    ```
1. Git clone the needed code
    ``` shell
    cd $env:SubspaceWorkspace\Clipper
    git clone https://github.com/jchamlin/clipper-java
    cd $env:SubspaceWorkspace\jMonkeyEngine
    git clone https://github.com/jMonkeyEngine-Contributions/Lemur.git
    git clone https://github.com/jMonkeyEngine-Contributions/zay-es.git
    git clone https://github.com/jMonkeyEngine/jmonkeyengine.git
    cd $env:SubspaceWorkspace\Simsilica
    git clone https://github.com/Simsilica/Pager.git
    git clone https://github.com/Simsilica/SimArboreal.git
    git clone https://github.com/Simsilica/SimFX.git
    git clone https://github.com/Simsilica/SimEthereal.git
    git clone https://github.com/Simsilica/SimMath.git
    git clone https://github.com/Simsilica/SiO2
    cd $env:SubspaceWorkspace\pspeed42
    git clone https://github.com/assofohdz/moss.git
    cd $env:SubspaceWorkspace
    git clone https://github.com/assofohdz/Subspace-Infinity.git
    
    ```
1. Build and install external dependencies
    ``` shell
    cd $env:SubspaceWorkspace\Clipper\clipper-java
    gradle publishToMavenLocal
    cd $env:SubspaceWorkspace\jMonkeyEngine
    git checkout v3.4.1-stable
    cd $env:SubspaceWorkspace\jMonkeyEngine\jmonkeyengine\jme3-core
    gradle install
    cd $env:SubspaceWorkspace\jMonkeyEngine\jmonkeyengine\jme3-networking
    gradle install
    cd $env:SubspaceWorkspace\jMonkeyEngine\Lemur
    gradle install
    cd $env:SubspaceWorkspace\jMonkeyEngine\Lemur\extensions\LemurProps
    gradle install
    cd $env:SubspaceWorkspace\jMonkeyEngine\Zay-ES
    gradle install
    cd $env:SubspaceWorkspace\jMonkeyEngine\Zay-ES\extensions\Zay-ES-Net
    gradle install    
    cd $env:SubspaceWorkspace\Simsilica\Pager
    gradle install
    cd $env:SubspaceWorkspace\Simsilica\SimArboreal
    gradle install
    cd $env:SubspaceWorkspace\Simsilica\SimEthereal
    gradle install
    cd $env:SubspaceWorkspace\Simsilica\SimFX
    gradle install
    cd $env:SubspaceWorkspace\Simsilica\SimMath
    gradle install
    cd $env:SubspaceWorkspace\Simsilica\SiO2
    gradle install
    cd $env:SubspaceWorkspace\pspeed42\moss
    gradle install
    
    ```
1. Build Subspace-Infinity
    ``` shell
    cd $env:SubspaceWorkspace\Subspace-Infinity
    gradle build
    
    ```
1. Run Subspace-Infinity
    ```shell
    cd $env:SubspaceWorkspace\Subspace-Infinity
    gradle run
    
    ```
