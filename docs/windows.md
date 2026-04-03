# Windows

## Minimum Requirements

- **64-bit Windows 10** (version 1703 or newer)
- Administrator access to install software

---

## Install Rust

1. Visit the official Rust website (https://rustup.rs/)
2. Download and install **rustup**
3. During installation, accept the default options

> [!NOTE]
> The Rust installer will offer to automatically download and install the required
> MSVC build tools. If you prefer a minimal install without the full Visual
> Studio IDE, do the following before installing Rust:
>
> 1. Install **Visual Studio Build Tools** via winget:
>    ```
>    winget install Microsoft.VisualStudio.BuildTools
>    ```
> 2. Open the **Visual Studio Installer**, select **Build Tools**, click
>    **Modify**, then under **Individual components**, install:
>    - **MSVC Build Tools for x64/x86 (Latest)**
>    - **Windows 11 SDK** (or Windows 10 SDK if you're on Windows 10)
>
> Then proceed with the Rust installation above.

> [!IMPORTANT]
> **Windows ARM users**: After installing rustup, run the following command in a terminal, inside the project folder:
>
> ```bash
> rustup target add x86_64-pc-windows-msvc
> ```

## Install MSYS2

1. Visit the [MSYS2](https://www.msys2.org/) website
2. Install it in the default location
3. After installation, open the MSYS2 UCRT64 terminal
4. Run the following command:

```bash
pacman -S git rsync
```

---

### Configure the PATH environment variable

1. Search for and open **Edit the system environment variables** in the Start menu
2. Click **Environment Variables**
3. Edit the **Path** variable under **System Variables**
4. Add the following path then reboot your computer: `C:\msys64\usr\bin`

> [!NOTE]
> If you have native Windows apps relying on Git, e.g. the PowerShell extension
> [posh-git](https://github.com/dahlbyk/posh-git), you may want to install
> [Git for Windows](https://gitforwindows.org/) and put it on the path instead,
> as msys Git may cause issues with them. You'll need to make sure rsync is
> available some other way.

## Choose a good source code location

Anki's source files do not need to be in a specific location, but it's best to
avoid long paths, as they can cause problems. Spaces in the path may cause
problems.

## More

For info on running tests, building wheels and so on, please see
[Development](./development.md).
