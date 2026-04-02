# Windows

## Minimum Requirements

- **64-bit Windows 10** (version 1703 or newer)
- Administrator access to install software

---

## Install Rust

1. Visit the official Rust website (https://rustup.rs/)
2. Download and install **rustup**
3. During installation, accept the default options

> [!IMPORTANT]
> **Windows ARM users**: After installing rustup, run the following command in a terminal, inside the project folder:
>
> ```bash
> rustup target add x86_64-pc-windows-msvc
> ```

## Install Visual Studio

1. Download **Visual Studio Community Edition**
2. Open the installer
3. Select **Desktop Development with C++** on the left
4. Leave the advanced options on the right unchanged

## Install MSYS2

1. Visit the [MSYS2](https://www.msys2.org/) website
2. Install it in the default location
3. After installation, open the MSYS2 terminal
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

## Audio Support (Optional)

To play and record audio during development, ensure the following executables are available in your **PATH**:

- `mpv.exe`
- `lame.exe`

## More

For info on running tests, building wheels and so on, please see
[Development](./development.md).
