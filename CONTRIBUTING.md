# Our Philosophy

EdgeCloudSim is an open source project, so every contributor is welcome!
EdgeCloudSim has not been fully tested regarding to many different scenarios.
There may be some bugs which are waiting to be fixed.
You can add new features on EdgeCloudSim as well.
Here is the list of needed features:

## Needed Features

* Mist computing features (executing tasks on mobile device)
* Incorporating cellular access network model into EdgeCloudSim (3G/4G/5G)
* Task migration among the Edge or Cloud VMs
* Energy consumption model for the mobile and edge devices as well as the cloud datacenters
* Adding probabilistic network failure model by considering the congestion or other parameters such as the distance between mobile device and the WiFi access point.
* Visual tool for displaying the network topology

# Reporting Bugs

Before reporting a bug, please search previously reported bugs to avoid duplication.
After you are sure that you found a non-reported bug, please create a new issue under the "Issues" section of the EdgeCloudSim project.
Please clearly describe the problem so that we can easily understand it.
We kindly request you to use following format while reporting bugs:
<pre>
<b>Description</b>
Clear explanation of the bug. What is the wrong behavior, and what is the expected one.

<b>Steps to reproduce</b>
please indicate the steps to reproduce related bug.

<b>Frequency</b>
Please state the the probability of occurrence of the bug, e.g.  rare, random, sometimes, always

<b>Additional Information</b>
If you want to specify more things, add additional notes here
</pre>

# How to Contribute to EdgeCloudSim

You can simply follow below steps to contribute to EdgeCloudSim:

1. Create a working copy (fork the project & clone it)
2. Specify a new remote upstream repository
3. Push your modification to the origin repository
4. Create a new pull request


Detailed explanation of the steps are given below:


## Step 1: Create a working copy (fork the project & clone it)
Open https://github.com/CagataySonmez/EdgeCloudSim on your browser and  press the "fork" button to create a copy of the repository in your own GitHub account.
Then find forked EdgeCloudSim project in your GitHub account and copy the url for cloning.
Finally clone this project by using 'git clone' command:
```
$ git clone https://github.com/YOUR_USERNAME/EdgeCloudSim.git
```

## Step 2: Specify a new remote upstream repository
In order to sync the a forked repository with the original repository, you need to set up a new remote that points to the original project.
Change directory to the forked repository and use 'git add remote' command to specify a new remote upstream repository.
```
$ cd EdgeCloudSim
$ git remote add upstream https://github.com/CagataySonmez/EdgeCloudSim.git
```

After this step, you will have two remotes on your local:

- _origin_: points to the forked project with read and write permissions.
- _upstream_: points to the main project with read permission only.


## Step 3: Push your modification to the origin repository

While you are working on your own fork, please use separate and isolated branches for each topic. For new features and improvements, make your pull request to the development branch on the upstream. If you are working on bug fixes or critical updates, make your pull request to the master branch.

**Please use meaningful branch names and commit messages so that we can easily understand what has been changed.**
You can use "bugfix/" prefix if you fix a bug, or use a "feature/" if you add a new feature.

For example, if you are fixing a bug, firstly sync with the upstream master branch:
```
$ git checkout master
$ git pull upstream master
```

If there are conflicts, resolve them and commit the merge. Then push the merge into your remote.
```
$ git push origin master
```

Now you are in sync with the upstream. Create a new branch and checkout to start working on the bug, then push your new branch to your remote
```
$ git checkout -b bugfix/issue#1234
$ git push -u origin bugfix/issue#1234
```

Implement your changes and commit them, then finally push them to the remote branch
```
$ git commit -m "issue#1234: prevent simulation freeze on oom"
$ git commit -m "issue#1234: prevent simulation freeze on timeout"
$ ...
$ git push origin bugfix/issue#1234
```


## Step 4: Create a new pull request

Open your GitHub repositories on your web browser, and find your forked EdgeCloudSim project.
Your new branch will be listed at the top and you will see "Compare & pull request" button next to your new branch.
Press this button, and create a pull request.
Please select select correct repository and branch for the **base fork** and **base** options.
According to the example given in step 3; "base fork" should be YOUR_USERNAME/EdgeCloudSim.git and "base" should be master.
**Please type sufficient title and description for your pull request.**
