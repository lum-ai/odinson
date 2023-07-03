---  
title: Contributing  
has_children: false  
nav_order: 14
--- 

# Contributing

Thank you for your interest in helping to improve Odinson!

If you're looking for a way to help, please take a look at [our open issues](https://github.com/lum-ai/odinson/issues).  For those new to the project, you may want to take a look at any issues labeled [`good first issue`](https://github.com/lum-ai/odinson/labels/good%20first%20issue).

When contributing to this repository, please first discuss the change you wish to make via issue, email, or any other method with the owners of this repository before making a change. Open communication helps us to avoid duplication of effort.

Before getting started, be sure to review our [Pull request Process](http://docs.lum.ai/odinson/contributing.html#pull-request-process) for a step-by-step explanation of how to get your changes reviewed and merged promptly.

Finally, please note we have a code of conduct that we expect everyone to follow throughout all interactions with the project and its community.

## Pull Request Process

Before a pull request can be accepted, all automated CI checks must pass. These include unit tests, linting, and coverage tests. When adding new functionality, it is important to include tests as part of your contribution to ensure the behavior is as intended and is not lost in future updates. See our [**testing**](/testing) page for details of how to write and run unit tests.

Before opening a new pull request, ensure that ...
1. You've added tests for any new functionality.
2. All tests pass locally via `sbt test`.
3. Linting is performed by running `sbt scalafmtAll scalafmtCheckAll`.  
   - You may want to [configure your code editor or IDE to format all code according the project style when saving](https://scalameta.org/scalafmt/docs/installation.html#format-on-save).
   - For some formatting tips, please see the next section.
4. You've updated [CHANGES.md](https://github.com/lum-ai/odinson/blob/master/CHANGES.md#unreleased) with a description of your contribution.

## Formatting Tips

Automatic formatting is not always optimal.  If you notice formatting that it is not just ugly or annoying for a particular section of code, but that significantly impedes understanding or hides errors that a different format would expose, consider these two options:
1. Wrap the section between [format comments](https://scalameta.org/scalafmt/docs/configuration.html#-format-off) `// format: off` and `// format: on`.
2. Temporarily override configuration settings with [scalafmt comments](https://scalameta.org/scalafmt/docs/configuration.html#for-code-block) `// scalafmt: {}`. 

Please consider use of these options to be recommendations.  They may be ruled out during the pull request process.  You may want to search for formatting comments in the code to familiarize yourself with their limited usage.  Here is one particularly good example for guidance:
```scala
def fancyFormattedQuantifiers(min, max) = {
  // format: off
  case (Some(min), Some(max), _) if min > max => Fail
  case (None,      maxOption, "}")  => Pass(GreedyQuantifier(  0, maxOption))
  case (Some(min), maxOption, "}")  => Pass(GreedyQuantifier(min, maxOption))
  case (None,      maxOption, "}?") => Pass(LazyQuantifier  (  0, maxOption))
  case (Some(min), maxOption, "}?") => Pass(LazyQuantifier  (min, maxOption))
  // format: on
}
```

# Contributor Covenant Code of Conduct

## Our Pledge

We as members, contributors, and leaders pledge to make participation in our
community a harassment-free experience for everyone.

We pledge to act and interact in ways that contribute to an open, welcoming,
diverse, inclusive, and healthy community.

## Our Standards

Examples of behavior that contributes to a positive environment for our
community include:

* Demonstrating empathy and kindness toward other people
* Being respectful of differing opinions, viewpoints, and experiences
* Giving and gracefully accepting constructive feedback
* Accepting responsibility and apologizing to those affected by our mistakes,
  and learning from the experience
* Focusing on what is best not just for us as individuals, but for the
  overall community

Examples of unacceptable behavior include:

* The use of sexualized language or imagery, and sexual attention or
  advances of any kind
* Trolling, insulting or derogatory comments, and personal or political attacks
* Public or private harassment
* Publishing others' private information, such as a physical or email
  address, without their explicit permission
* Other conduct which could reasonably be considered inappropriate in a
  professional setting

## Enforcement Responsibilities

Community leaders are responsible for clarifying and enforcing our standards of
acceptable behavior and will take appropriate and fair corrective action in
response to any behavior that they deem inappropriate, threatening, offensive,
or harmful.

Community leaders have the right and responsibility to remove, edit, or reject
comments, commits, code, wiki edits, issues, and other contributions that are
not aligned to this Code of Conduct, and will communicate reasons for moderation
decisions when appropriate.

## Scope

This Code of Conduct applies within all community spaces, and also applies when
an individual is officially representing the community in public spaces.
Examples of representing our community include using an official e-mail address,
posting via an official social media account, or acting as an appointed
representative at an online or offline event.

## Enforcement

Instances of abusive, harassing, or otherwise unacceptable behavior may be
reported to the community leaders responsible for enforcement at
`dev AT lum DOT ai`.
All complaints will be reviewed and investigated promptly and fairly.

All community leaders are obligated to respect the privacy and security of the
reporter of any incident.

## Enforcement Guidelines

Community leaders will follow these Community Impact Guidelines in determining
the consequences for any action they deem in violation of this Code of Conduct:

### 1. Correction

**Community Impact**: Use of inappropriate language or other behavior deemed
unprofessional or unwelcome in the community.

**Consequence**: A private, written warning from community leaders, providing
clarity around the nature of the violation and an explanation of why the
behavior was inappropriate. A public apology may be requested.

### 2. Warning

**Community Impact**: A violation through a single incident or series
of actions.

**Consequence**: A warning with consequences for continued behavior. No
interaction with the people involved, including unsolicited interaction with
those enforcing the Code of Conduct, for a specified period of time. This
includes avoiding interactions in community spaces as well as external channels
like social media. Violating these terms may lead to a temporary or
permanent ban.

### 3. Temporary Ban

**Community Impact**: A serious violation of community standards, including
sustained inappropriate behavior.

**Consequence**: A temporary ban from any sort of interaction or public
communication with the community for a specified period of time. No public or
private interaction with the people involved, including unsolicited interaction
with those enforcing the Code of Conduct, is allowed during this period.
Violating these terms may lead to a permanent ban.

### 4. Permanent Ban

**Community Impact**: Demonstrating a pattern of violation of community
standards, including sustained inappropriate behavior, harassment of an
individual, or aggression toward or disparagement of classes of individuals.

**Consequence**: A permanent ban from any sort of public interaction within
the community.

## Attribution

This Code of Conduct is adapted from the [Contributor Covenant][homepage],
version 2.0, available at
[https://www.contributor-covenant.org/version/2/0/code_of_conduct.html][v2.0].

Community Impact Guidelines were inspired by 
[Mozilla's code of conduct enforcement ladder][Mozilla CoC].

For answers to common questions about this code of conduct, see the FAQ at
[https://www.contributor-covenant.org/faq][FAQ]. Translations are available 
at [https://www.contributor-covenant.org/translations][translations].

[homepage]: https://www.contributor-covenant.org
[v2.0]: https://www.contributor-covenant.org/version/2/0/code_of_conduct.html
[Mozilla CoC]: https://github.com/mozilla/diversity
[FAQ]: https://www.contributor-covenant.org/faq
[translations]: https://www.contributor-covenant.org/translations
