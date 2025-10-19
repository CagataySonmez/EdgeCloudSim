# Contributing to EdgeCloudSim

Thank you for your interest in contributing to EdgeCloudSim! We welcome contributions from the community and are pleased to have you join us.

## Table of Contents
- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Enhancements](#suggesting-enhancements)
- [Development Setup](#development-setup)
- [Pull Request Process](#pull-request-process)

## Code of Conduct

This project and everyone participating in it is governed by our commitment to creating a welcoming and inclusive environment. Please be respectful and constructive in all interactions.

## How Can I Contribute?

### Types of Contributions We Welcome:
- 🐛 **Bug fixes**: Help us identify and fix issues
- ✨ **New features**: Add functionality that benefits the community
- 📚 **Documentation**: Improve existing docs or add new ones
- 🧪 **Testing**: Add test cases or improve test coverage
- 🔧 **Code quality**: Refactoring, performance improvements

EdgeCloudSim is actively developed and tested across many scenarios, but there may still be edge cases and bugs waiting to be discovered.

## Reporting Bugs

We use GitHub Issues to track bugs. Before creating a new issue:

1. **Search existing issues** to avoid duplicates
2. **Check the latest version** to see if the issue persists
3. **Gather system information** (OS, Java version, EdgeCloudSim version)

### Bug Report Template

When creating a bug report, please include:

```markdown
**Description**
A clear and concise description of what the bug is.

**Environment**
- OS: [e.g. Ubuntu 20.04, Windows 10]
- Java Version: [e.g. OpenJDK 11]
- EdgeCloudSim Version: [e.g. v4.0]

**Steps to Reproduce**
1. Go to '...'
2. Click on '....'
3. Run command '....'
4. See error

**Expected Behavior**
A clear description of what you expected to happen.

**Actual Behavior**
A clear description of what actually happened.

**Additional Context**
- Error logs/stack traces
- Configuration files (if relevant)
- Screenshots (if applicable)
```

## Suggesting Enhancements

We welcome feature requests! Please:

1. **Check existing issues** for similar requests
2. **Describe the problem** your feature would solve
3. **Explain your proposed solution** in detail
4. **Consider the scope** - will this benefit many users?

## Development Setup

### Prerequisites
- Java 8 or higher
- Eclipse IDE (recommended) or any Java IDE
- Git

### Setting Up Your Environment

1. **Fork and Clone**
   ```bash
   git clone https://github.com/YOUR_USERNAME/EdgeCloudSim.git
   cd EdgeCloudSim
   ```

2. **Add Upstream Remote**
   ```bash
   git remote add upstream https://github.com/CagataySonmez/EdgeCloudSim.git
   ```

3. **Import into IDE**
   - Open Eclipse/IntelliJ
   - Import as existing Java project
   - Ensure all dependencies in `lib/` folder are included

4. **Verify Setup**
   - Run a sample application to ensure everything works
   - Check that all imports resolve correctly

## Pull Request Process

### ⚠️ IMPORTANT: All pull requests MUST target the `development` branch

### Workflow Overview

### Workflow Overview

1. **Create a feature branch** from `development`
2. **Make your changes** with clear commits
3. **Test thoroughly** before submitting
4. **Submit a pull request** to `development` branch
5. **Respond to review feedback** if needed

### Detailed Steps

#### 1. Sync with Upstream Development Branch
```bash
git checkout development
git pull upstream development
git push origin development
```

#### 2. Create a Feature Branch
Use descriptive branch names with prefixes:
- `feature/` for new features
- `bugfix/` for bug fixes  
- `docs/` for documentation
- `test/` for testing improvements

```bash
git checkout -b feature/new-mobility-model
git push -u origin feature/new-mobility-model
```

#### 3. Make Your Changes

**Commit Guidelines:**
- Write clear, descriptive commit messages
- Use present tense ("Add feature" not "Added feature")
- Reference issues when applicable (`Fixes #123`)
- Keep commits atomic (one logical change per commit)

```bash
git commit -m "feat: implement random waypoint mobility model

- Add RandomWaypointMobility class
- Update MobilityModule to support new model
- Add configuration parameters for waypoint generation

Fixes #45"
```

#### 4. Test Your Changes

Before submitting:
- [ ] Code compiles without errors
- [ ] All existing tests pass
- [ ] New functionality has been tested
- [ ] Documentation updated if needed

#### 5. Submit Pull Request

**Target Branch:** Always select `development` as the base branch

**PR Checklist:**
- [ ] Branch is up-to-date with `development`
- [ ] All tests pass
- [ ] Code follows project conventions
- [ ] Documentation updated
- [ ] Clear description of changes

**PR Template:**
```markdown
## Description
Brief description of changes made.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation update
- [ ] Performance improvement
- [ ] Other (please describe)

## Testing
Describe how you tested your changes.

## Related Issues
Closes #(issue number)
```

### Review Process

1. **Automated Checks**: Your PR will be automatically checked
2. **Code Review**: Maintainers will review your changes
3. **Feedback**: Address any requested changes
4. **Approval**: Once approved, your PR will be merged

### Branch Protection Rules

- ❌ **Direct pushes to `master`** are not allowed
- ❌ **Pull requests to `master`** will be rejected
- ✅ **All changes must go through `development`** branch
- ✅ **Code review is required** before merging

## Questions?

If you have questions about contributing:
- Check existing [Issues](https://github.com/CagataySonmez/EdgeCloudSim/issues)
- Create a [Discussion](https://groups.google.com/g/edgecloudsim)
- Contact the maintainers

Thank you for contributing to EdgeCloudSim! 🚀
