# Windows Quick Start Guide

> A simplified and beginner-friendly version of Anki’s official Windows documentation.
> Ideal for first-time contributors building Anki on Windows.

---

## Minimum Requirements

- **64-bit Windows 10** (version 1703 or newer)
- Administrator access to install software

---

## Step 1 – Install Rust (rustup)

Anki uses the **Rust programming language** for parts of its codebase.

1. Visit the official Rust website
2. Download and install **rustup**
3. During installation, accept the default options

> **Windows ARM users**\
> After installing rustup, run the following command in a terminal, inside the project folder:
>
> ```bash
> rustup target add x86_64-pc-windows-msvc
> ```

---

## Step 2 – Install Visual Studio

1. Download **Visual Studio Community Edition**
2. Open the installer
3. Select:
   - **Desktop Development with C++**
4. Leave the advanced options unchanged

> **Note**
> This step is required to compile native parts of the project.
> On Windows, compiling native code requires Microsoft's MSVC toolchain and the Windows SDK
> (system headers and libraries), which are typically installed via Visual Studio or
> Visual Studio Build Tools.

---

## Step 3 – Install MSYS2

1. Visit the [MSYS2](https://www.msys2.org/) website
2. Install it in the default location
3. After installation, open the MSYS2 terminal
4. Run the following command:

```bash
pacman -S git rsync
```

---

### Configure the PATH Environment Variable

1. Open **Windows Environment Variables**
2. Edit the **PATH** variable
3. Add the following path:

```text
C:\msys64\usr\bin
```

4. Reboot your computer

> **Important note**\
> If you already have native Windows apps relying on Git (for example, [posh-git](https://github.com/dahlbyk/posh-git) in PowerShell), they may conflict with the MSYS2 Git.
>
> - In that case, prefer using [Git for Windows](https://gitforwindows.org/) over msys2.
> - Ensure that `rsync` is available some other way

---

## Step 4 – Choose a Good Source Code Location

To avoid build issues:

- Avoid long directory paths
- Avoid spaces in folder names

**Recommended**:

```text
C:\anki
```

**Problematic**:

```text
C:\Users\Your Name\Documents\Projects\Anki Source Code
```

---

## Audio Support (Optional)

To play and record audio during development, ensure the following executables are available in your **PATH**:

- `mpv.exe`
- `lame.exe`

---

## Common Problems

### Build errors

- Make sure Visual Studio was installed with **Desktop Development with C++**

### `git` command not found

- Verify that the PATH variable is configured correctly

### Issues caused by spaces in paths

- Move the project to a shorter path, such as `C:\anki`

---

## Next Steps

For advanced topics such as running tests or building wheels, see:

- `development.md`

---
