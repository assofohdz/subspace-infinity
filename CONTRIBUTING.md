# Contributing

## Setting up your Development Environment

These instructions assume you are using Windows 10. Java is cross-platform and Subspace-Infinity should be able to build and work on any platform, but it has only been developed and tested on Windows 10. Please use the Eclipse IDE. Eclipse provides built-in code formatting and ensures code adheres to coding style guidelines. It also has built-in static code analysis (SCA) to help detect and prevent errors from being introduced into the product.

1. Follow the steps in [BUILDING.md](BUILDING.md) to get Chocolatey, git, Open JDK, gradle, and clone and build the code.
1. Launch [PowerShell](https://docs.microsoft.com/en-us/PowerShell/scripting/windows-PowerShell/install/installing-windows-PowerShell?view=PowerShell-7) as Administrator, or use [Windows Terminal](https://www.microsoft.com/en-us/p/windows-terminal/9n0dx20hk701) run as Administrator.
1. Install Eclipse: `choco install eclipse-java-oxygen`
1. Close your Administrator PowerShell and open a new PowerShell session
1. Launch Eclipse by typing `eclipse` at the PowerShell command prompt
1. Eclipse will prompt you for a workspace folder, select the workspace folder you created when you followed the build instructions, for example: `C:\workspace\Subspace`
1. Use the Eclipse Marketplace: `Help->Eclipse Marketplace...` to install `Yaml Editor 1.6.2` and `Checkstyle Plug-in 8.34.0`
1. Import the Workspace Preferences with `File->Import->General->Preferences`, and select the file `C:\workspace\Subspace\Subspace-Infinity\eclipse\Workspace-Preferences.epf`. Eclipse will suggest you restart, do that.
1. Import the Subspace-Infinity checkstyle xml via `Window->Preferences->Checkstyle->New`, choose the `Subspace-Infinity\checkstyle.xml` and set the new checkstyle to Default
1. Import Subspace-Infinity into Eclipse using `File->Import->Gradle->Existing Gradle Project` and select the folder `C:\workspace\Subspace\Subspace-Infinity`
1. Let Eclipse finish importing and building Subspace-Infinity before proceeding to the next step
1. If you want to view projects in a hierarchical view instead of flat, select `Window->Show View->Project Explorer`
1. Select the infinity folder within the Subspace-Infinity project, right click, and select Run As...->Java Application, select the class `infinity.Main` as the class to run, name the run configuration `Subspace-Infinity Main`, and put the following in for the VM Arguments parameter: `-Xmx1024m -Xms512m -XX:MaxDirectMemorySize=1024m --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED`
1. Click the `Run` button and Subspace-Infinity should launch, with logging appearing in the Eclipse console window

## Guildelines for Submitting PRs

### Issues

The [issues page](https://github.com/assofohdz/Subspace-Infinity/issues) on GitHub is for tracking bugs and feature requests. When posting a new issue, please:

* Check to make sure it's not a duplicate of an existing issue.
* Create a separate "issue" for each bug you are reporting and each feature you are requesting.
* Do not use the issues page for things other than bug reports and feature requests.

If requesting a new feature, first ask yourself: will this make the game more fun or interesting? Remember that this is a game, not a simulator. Changes will not be made purely for the sake of realism, especially if they introduce needless complexity or aggravation.

### Pull Requests

If you are posting a pull request, please:

* Do not combine multiple unrelated changes into a single pull.
* Check the diff and make sure the pull request does not contain unintended changes.
* If changing the Java code, use Eclipse to ensure the code follows the style guidelines.

If proposing a major pull request, start by posting an issue and discussing the best way to implement it. Often the first strategy that occurs to you will not be the cleanest or most effective way to implement a new feature. I will not merge pull requests that are too large for me to read through the diff and check that the change will not introduce bugs.

### Closing Issues

If you believe your issue has been resolved, you can close the issue yourself. I won't close an issue unless it has been idle for a few weeks, to avoid having me mark something as fixed when the original poster does not think their request has been fully addressed.

If an issue is a bug and it has been fixed in the code, it may be helpful to leave it "open" until an official release that fixes the bug has been made, so that other people encountering the same bug will see that it has already been reported.

### Issue Labels

The labels that are assigned to issues are:

* bug: Anything where the game is not behaving as intended.
* documentation: Something missing or incorrect in the game documentation.
* balance: A ship or weapon that seems too powerful or useless, or a mission that seems too easy or hard.
* mechanics: A question of whether the game mechanics should be altered.
* enhancement: A request for new functionality in the game engine itself.
* content: A suggestion for new content that could be created without changing the game code.
* question: A question of how something works, or a support question.
* unlikely: An enhancement or other change that is lowest priority or too large or difficult.
* unconfirmed: More information is needed to be sure this bug is really a bug.
* wontfix: A change that definitely will not be made.
