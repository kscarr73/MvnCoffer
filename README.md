Purpose
=======


A simple Maven Repository, with Simple Security.

Features
========


* Upload new files via Web Post
* List files in a simple view through browser

Installation
============

```
install mvn:com.progbits.mvn/MvnCoffer/1.0.4
```

Configuration
=============

Create a file in ${karaf.base}/etc called MvnCofferServlet.cfg
```
repoDir={Path to Repo}
user_test=test;internal_WRITE
user_otheruser=newpass;internal_WRITE
```