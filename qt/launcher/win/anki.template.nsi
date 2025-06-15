;; This installer was written many years ago, and it is probably worth investigating modern
;; installer alternatives at one point.

!addplugindir .

!include "fileassoc.nsh"
!include WinVer.nsh
!include x64.nsh

!define nsProcess::FindProcess `!insertmacro nsProcess::FindProcess`

!macro nsProcess::FindProcess _FILE _ERR
	nsProcess::_FindProcess /NOUNLOAD `${_FILE}`
	Pop ${_ERR}
!macroend

;--------------------------------

!pragma warning disable 6020 ; don't complain about missing installer in second invocation

; The name of the installer
Name "Anki"

Unicode true

; The file to write (relative to nsis directory)
OutFile "..\launcher_exe\anki-install.exe"

; Non elevated
RequestExecutionLevel user

; The default installation directory
InstallDir "$LOCALAPPDATA\Programs\Anki"

; Remember the install location
InstallDirRegKey HKCU "Software\Anki" "Install_Dir64"

AllowSkipFiles off

!ifdef NO_COMPRESS
SetCompress off
!else
SetCompressor /solid lzma
!endif

Function .onInit
  ${IfNot} ${AtLeastWin10}
    MessageBox MB_OK "Windows 10 or later required."
    Quit
  ${EndIf}

  ${IfNot} ${RunningX64}
    MessageBox MB_OK "64bit Windows is required."
    Quit
  ${EndIf}

  ${nsProcess::FindProcess} "anki.exe" $R0
  StrCmp $R0 0 0 notRunning
      MessageBox MB_OK|MB_ICONEXCLAMATION "Anki.exe is already running. Please close it, then run the installer again." /SD IDOK
      Abort
  notRunning:
FunctionEnd

!ifdef WRITE_UNINSTALLER
!uninstfinalize 'copy "%1" "uninstall.exe"'
!endif

;--------------------------------

; Pages

Page directory
Page instfiles


;; manifest removal script shared by installer and uninstaller
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

!define UninstLog "anki.install-manifest"
Var UninstLog

!macro removeManifestFiles un
Function ${un}removeManifestFiles
  IfFileExists "$INSTDIR\${UninstLog}" proceed
    DetailPrint "No previous install manifest found, skipping cleanup."
    return

;; this code was based on an example found on the net, which I can no longer find
proceed:
  Push $R0
  Push $R1
  Push $R2
  SetFileAttributes "$INSTDIR\${UninstLog}" NORMAL
  FileOpen $UninstLog "$INSTDIR\${UninstLog}" r
  StrCpy $R1 -1

  GetLineCount:
    ClearErrors
    FileRead $UninstLog $R0
    IntOp $R1 $R1 + 1
    StrCpy $R0 $R0 -2
    Push $R0
    IfErrors 0 GetLineCount

  Pop $R0

  LoopRead:
    StrCmp $R1 0 LoopDone
    Pop $R0
    ;; manifest is relative to instdir
    StrCpy $R0 "$INSTDIR\$R0"

    IfFileExists "$R0\*.*" 0 +3
      RMDir $R0  #is dir
    Goto processed
    IfFileExists $R0 0 +3
      Delete $R0 #is file
    Goto processed

processed:

    IntOp $R1 $R1 - 1
    Goto LoopRead
  LoopDone:
  FileClose $UninstLog
  Delete "$INSTDIR\${UninstLog}"
  RMDir "$INSTDIR"
  Pop $R2
  Pop $R1
  Pop $R0
FunctionEnd
!macroend

!insertmacro removeManifestFiles ""
!insertmacro removeManifestFiles "un."

;--------------------------------

; Macro from fileassoc changed to work non elevated
!macro APP_ASSOCIATE_HKCU EXT FILECLASS DESCRIPTION ICON COMMANDTEXT COMMAND
  ; Backup the previously associated file class
  ReadRegStr $R0 HKCU "Software\Classes\.${EXT}" ""
  WriteRegStr HKCU "Software\Classes\.${EXT}" "${FILECLASS}_backup" "$R0"
 
  WriteRegStr HKCU "Software\Classes\.${EXT}" "" "${FILECLASS}"
 
  WriteRegStr HKCU "Software\Classes\${FILECLASS}" "" `${DESCRIPTION}`
  WriteRegStr HKCU "Software\Classes\${FILECLASS}\DefaultIcon" "" `${ICON}`
  WriteRegStr HKCU "Software\Classes\${FILECLASS}\shell" "" "open"
  WriteRegStr HKCU "Software\Classes\${FILECLASS}\shell\open" "" `${COMMANDTEXT}`
  WriteRegStr HKCU "Software\Classes\${FILECLASS}\shell\open\command" "" `${COMMAND}`
!macroend

; Macro from fileassoc changed to work non elevated
!macro APP_UNASSOCIATE_HKCU EXT FILECLASS
  ; Backup the previously associated file class
  ReadRegStr $R0 HKCU "Software\Classes\.${EXT}" `${FILECLASS}_backup`
  WriteRegStr HKCU "Software\Classes\.${EXT}" "" "$R0"
 
  DeleteRegKey HKCU `Software\Classes\${FILECLASS}`
!macroend

; The stuff to install
Section ""

  SetShellVarContext current

  ; "Upgrade" from elevated anki
  ReadRegStr $0 HKLM "Software\WOW6432Node\Anki" "Install_Dir64"
  ${IF} $0 != ""
      ; old value exists, we want to inform the user that a manual uninstall is required first and then start the uninstall.exe
      MessageBox MB_ICONEXCLAMATION|MB_OK "A previous Anki version needs to be uninstalled first. After uninstallation completes, please run this installer again."
      ClearErrors
      ExecShell "open" "$0\uninstall.exe"
      IfErrors shellError
      Quit
  ${ELSE}
      goto notOldUpgrade
  ${ENDIF}

  shellError:
    MessageBox MB_OK|MB_ICONEXCLAMATION "Failed to uninstall the old version of Anki. Proceeding with installation."
  notOldUpgrade:

  Call removeManifestFiles

  ; Set output path to the installation directory.
  SetOutPath $INSTDIR
  CreateShortCut "$DESKTOP\Anki.lnk" "$INSTDIR\anki.exe" ""
  CreateShortCut "$SMPROGRAMS\Anki.lnk" "$INSTDIR\anki.exe" ""

  ; Add files to installer
  !ifndef WRITE_UNINSTALLER
  File /r ..\launcher\*.*
  !endif

  !insertmacro APP_ASSOCIATE_HKCU "apkg" "anki.apkg" \
    "Anki deck package" "$INSTDIR\anki.exe,0" \
    "Open with Anki" "$INSTDIR\anki.exe $\"%L$\""
  
  !insertmacro APP_ASSOCIATE_HKCU "colpkg" "anki.colpkg" \
    "Anki collection package" "$INSTDIR\anki.exe,0" \
    "Open with Anki" "$INSTDIR\anki.exe $\"%L$\""

  !insertmacro APP_ASSOCIATE_HKCU "ankiaddon" "anki.ankiaddon" \
    "Anki add-on" "$INSTDIR\anki.exe,0" \
    "Open with Anki" "$INSTDIR\anki.exe $\"%L$\""

  !insertmacro UPDATEFILEASSOC

  ; Write the installation path into the registry
  ; WriteRegStr HKLM Software\Anki "Install_Dir64" "$INSTDIR"
  WriteRegStr HKCU Software\Anki "Install_Dir64" "$INSTDIR"

  ; Write the uninstall keys for Windows
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "DisplayName" "Anki Launcher"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "DisplayVersion" "1.0.0"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "QuietUninstallString" '"$INSTDIR\uninstall.exe" /S'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "NoRepair" 1

  !ifdef WRITE_UNINSTALLER
  WriteUninstaller "uninstall.exe"
  !endif

SectionEnd ; end the section

;--------------------------------

; Uninstaller

function un.onInit
   MessageBox MB_OKCANCEL "This will remove Anki's program files, but will not delete your card data. If you wish to delete your card data as well, you can do so via File>Switch Profile inside Anki first. Are you sure you wish to uninstall Anki?" /SD IDOK IDOK next
      Quit
  next:
functionEnd

Section "Uninstall"

  SetShellVarContext current

  Call un.removeManifestFiles

  ; Remove other shortcuts
  Delete "$DESKTOP\Anki.lnk"
  Delete "$SMPROGRAMS\Anki.lnk"

  ; associations
  !insertmacro APP_UNASSOCIATE_HKCU "apkg" "anki.apkg"
  !insertmacro APP_UNASSOCIATE_HKCU "colpkg" "anki.colpkg"
  !insertmacro APP_UNASSOCIATE_HKCU "ankiaddon" "anki.ankiaddon"
  !insertmacro UPDATEFILEASSOC

  ; Schedule uninstaller for deletion on reboot
  Delete /REBOOTOK "$INSTDIR\uninstall.exe"
  
  ; try to remove top level folder if empty
  RMDir "$INSTDIR"

  ; Remove AnkiProgramData folder created during runtime
  RMDir /r "$LOCALAPPDATA\AnkiProgramFiles"

  ; Remove registry keys
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki"
  DeleteRegKey HKCU Software\Anki

SectionEnd
