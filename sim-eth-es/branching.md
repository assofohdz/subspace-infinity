Here's a little walkthrough of how Yannick and I are using feature branches and pull requests to develop new features and adding them to the project. Below are the steps I take when working on a new feature. Hopefully this, along with watching the process on Github, will serve as a starting point to having everyone use a similar workflow. 

Questions, comments, and suggestions for improvements welcome!

## Start with the latest on master

When starting a new feature, I make sure to start with the latest and greatest codebase:

```
git checkout master
git pull origin master
```

This reduces complications of dealing with out of date code, and reduces the chances of merge issues.

## Create feature branch

Now I develop a local branch to house the changes required for the new feature. 

Here we are using the term 'feature' loosely. Its a logical grouping of code and configuration changes to enable a new portion of the code, fix an issue, or improve existing code. The idea is to use your best judgement and try to keep the scope of the changes limited to a single logical issue.

```
git checkout -b add_linting
```

This will create a new branch called `add_linting` and check it out for me. 

We could argue about branch naming practices, but so far I haven't found naming to be that big of an issue.

```
git status
```

Will show we are on the new branch and ready to work

## Modify code

Now we implement the new feature / bug fix. Work as you would normally, making small incremental changes and checking them into the local feature branch.

Use descriptive comments when adding new changes so that the history of changes is easy to follow. They can still be short and succinct - but clear.

## Push Feature Branch to Remote

Ok, you are done with the implementation. You've checked and double checked the changes, and are ready to have them integrated into the main code base. 

The first step of the review process is to push your feature branch to `origin`.

```
git push origin add_linting
```

This will push your current branch to a new branch on origin with the same name. 

Of course you can do this multiple times during the development process - if you want the peace of mind of having your changes distributed, or you want another set of eyes on it even before the pull request.

## Create Pull Request

With your feature branch on github, navigate to the project on github. On the main page, you should see a new little toolbar that shows your feature branch listed and asks if you want to create a pull request from it. So let's do it!

![screen shot 2015-05-27 at 10 28 45 am](https://cloud.githubusercontent.com/assets/9369/7843160/ae17dcf2-045c-11e5-9f12-db8f4b197137.png)


When creating a pull request, you want to summarize the changes being made for this new feature and give it a descriptive title. 

You can reference existing issues or other PR's by typing # - and then the issue number. A little pop-up should help with picking the right issue number. 

Feel free to add screenshots or other images if there are visual changes associated with your PR. 

Once you have written out the description for the new PR - submit it and sit back for a bit while a teammate reviews. 

Next up, we will look at the PR review process and how it can be done efficiently on github.
