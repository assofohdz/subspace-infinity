# Contributing

## Setting up your Development Environment

These instructions assume you are using Windows 10. Java is cross-platform and Subspace-Infinity should be able to build and work on any platform, but it has only been developed and tested on Windows 10. 

I am currently using IntelliJ IDEA to develop with the following plugins/extensions:
1. Google Code Style
2. SonarLint

If you're looking to contribute and want to get a more thorough understand of the underlying frameworks, here's a small list to go through. Some parts can probably be skipped or skimmed pretty quickly if you're familiar with Java, with game development concept, with scene graphs etc.
1. [JMonkeyEngine Docs](https://wiki.jmonkeyengine.org/docs/3.4/documentation.html) - Important! The scene graph engine used as client
2. [SimEthereal](https://github.com/Simsilica/SimEthereal/wiki) - Provides the networking layer, can be skipped
3. [SiO2](https://github.com/Simsilica/SiO2) - Important! Provides the foundational GameLoop and GameSystemManager
4. [Zay-ES](http://jmonkeyengine-contributions.github.io/zay-es/) - Important! The Entity System that Subspace Infinity builds on
5. [Lemur](http://jmonkeyengine-contributions.github.io/Lemur/) - The GUI library

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
