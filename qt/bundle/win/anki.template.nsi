;; This installer was written many years ago, and it is probably worth investigating modern
;; installer alternatives. 

!include "fileassoc.nsh"
!include WinVer.nsh
!include x64.nsh
; must be installed into NSIS install location
; can be found on https://github.com/ankitects/anki-bundle-extras/releases/tag/anki-2022-02-09
!include nsProcess.nsh

;--------------------------------

!pragma warning disable 6020 ; don't complain about missing installer in second invocation

; The name of the installer
Name "Anki"

Unicode true

; The file to write (make relative to repo root instead of out/bundle)
OutFile "..\..\@@INSTALLER@@"

; The default installation directory
InstallDir "$PROGRAMFILES64\Anki"

; Remember the install location
InstallDirRegKey HKLM "Software\Anki" "Install_Dir64"

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
!uninstfinalize 'copy "%1" "std\uninstall.exe"'
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

; The stuff to install
Section ""

  SetShellVarContext all

  Call removeManifestFiles

  ; Set output path to the installation directory.
  SetOutPath $INSTDIR
  CreateShortCut "$DESKTOP\Anki.lnk" "$INSTDIR\anki.exe" ""
  CreateShortCut "$SMPROGRAMS\Anki.lnk" "$INSTDIR\anki.exe" ""

  ; Add files to installer
  !ifndef WRITE_UNINSTALLER
  File /r ..\..\@@SRC@@\*.*
  !endif

  !insertmacro APP_ASSOCIATE "apkg" "anki.apkg" \
    "Anki deck package" "$INSTDIR\anki.exe,0" \
    "Open with Anki" "$INSTDIR\anki.exe $\"%L$\""
  
  !insertmacro APP_ASSOCIATE "colpkg" "anki.colpkg" \
    "Anki collection package" "$INSTDIR\anki.exe,0" \
    "Open with Anki" "$INSTDIR\anki.exe $\"%L$\""

  !insertmacro APP_ASSOCIATE "ankiaddon" "anki.ankiaddon" \
    "Anki add-on" "$INSTDIR\anki.exe,0" \
    "Open with Anki" "$INSTDIR\anki.exe $\"%L$\""

  !insertmacro UPDATEFILEASSOC

  ; Write the installation path into the registry
  WriteRegStr HKLM Software\Anki "Install_Dir64" "$INSTDIR"

  ; Write the uninstall keys for Windows
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "DisplayName" "Anki"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "DisplayVersion" "@@VERSION@@"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki" "NoRepair" 1

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

  SetShellVarContext all

  Call un.removeManifestFiles

  ; Remove other shortcuts
  Delete "$DESKTOP\Anki.lnk"
  Delete "$SMPROGRAMS\Anki.lnk"

  ; associations
  !insertmacro APP_UNASSOCIATE "apkg" "anki.apkg"
  !insertmacro APP_UNASSOCIATE "colpkg" "anki.colpkg"
  !insertmacro APP_UNASSOCIATE "ankiaddon" "anki.ankiaddon"
  !insertmacro UPDATEFILEASSOC

  ; try to remove top level folder if empty
  RMDir "$INSTDIR"

  ; Remove registry keys
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Anki"
  DeleteRegKey HKLM Software\Anki

SectionEnd
