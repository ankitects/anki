; fileassoc.nsh
; https://nsis.sourceforge.io/File_Association
; File association helper macros
; Written by Saivert
;
; Features automatic backup system and UPDATEFILEASSOC macro for
; shell change notification.
;
; |> How to use <|
; To associate a file with an application so you can double-click it in explorer, use
; the APP_ASSOCIATE macro like this:
;
;   Example:
;   !insertmacro APP_ASSOCIATE "txt" "myapp.textfile" "$INSTDIR\myapp.exe,0" \
;     "Open with myapp" "$INSTDIR\myapp.exe $\"%1$\""
;
; Never insert the APP_ASSOCIATE macro multiple times, it is only meant
; to associate an application with a single file and using the
; the "open" verb as default. To add more verbs (actions) to a file
; use the APP_ASSOCIATE_ADDVERB macro.
;
;   Example:
;   !insertmacro APP_ASSOCIATE_ADDVERB "myapp.textfile" "edit" "Edit with myapp" \
;     "$INSTDIR\myapp.exe /edit $\"%1$\""
;
; To have access to more options when registering the file association use the
; APP_ASSOCIATE_EX macro. Here you can specify the verb and what verb is to be the
; standard action (default verb).
;
; And finally: To remove the association from the registry use the APP_UNASSOCIATE
; macro. Here is another example just to wrap it up:
;   !insertmacro APP_UNASSOCIATE "txt" "myapp.textfile"
;
; |> Note <|
; When defining your file class string always use the short form of your application title
; then a period (dot) and the type of file. This keeps the file class sort of unique.
;   Examples:
;   Winamp.Playlist
;   NSIS.Script
;   Photoshop.JPEGFile
;
; |> Tech info <|
; The registry key layout for a file association is:
; HKEY_CLASSES_ROOT
;     <applicationID> = <"description">
;         shell
;             <verb> = <"menu-item text">
;                 command = <"command string">
;
 
!macro APP_ASSOCIATE EXT FILECLASS DESCRIPTION ICON COMMANDTEXT COMMAND
  ; Backup the previously associated file class
  ReadRegStr $R0 HKCR ".${EXT}" ""
  WriteRegStr HKCR ".${EXT}" "${FILECLASS}_backup" "$R0"
 
  WriteRegStr HKCR ".${EXT}" "" "${FILECLASS}"
 
  WriteRegStr HKCR "${FILECLASS}" "" `${DESCRIPTION}`
  WriteRegStr HKCR "${FILECLASS}\DefaultIcon" "" `${ICON}`
  WriteRegStr HKCR "${FILECLASS}\shell" "" "open"
  WriteRegStr HKCR "${FILECLASS}\shell\open" "" `${COMMANDTEXT}`
  WriteRegStr HKCR "${FILECLASS}\shell\open\command" "" `${COMMAND}`
!macroend
 
!macro APP_ASSOCIATE_EX EXT FILECLASS DESCRIPTION ICON VERB DEFAULTVERB SHELLNEW COMMANDTEXT COMMAND
  ; Backup the previously associated file class
  ReadRegStr $R0 HKCR ".${EXT}" ""
  WriteRegStr HKCR ".${EXT}" "${FILECLASS}_backup" "$R0"
 
  WriteRegStr HKCR ".${EXT}" "" "${FILECLASS}"
  StrCmp "${SHELLNEW}" "0" +2
  WriteRegStr HKCR ".${EXT}\ShellNew" "NullFile" ""
 
  WriteRegStr HKCR "${FILECLASS}" "" `${DESCRIPTION}`
  WriteRegStr HKCR "${FILECLASS}\DefaultIcon" "" `${ICON}`
  WriteRegStr HKCR "${FILECLASS}\shell" "" `${DEFAULTVERB}`
  WriteRegStr HKCR "${FILECLASS}\shell\${VERB}" "" `${COMMANDTEXT}`
  WriteRegStr HKCR "${FILECLASS}\shell\${VERB}\command" "" `${COMMAND}`
!macroend
 
!macro APP_ASSOCIATE_ADDVERB FILECLASS VERB COMMANDTEXT COMMAND
  WriteRegStr HKCR "${FILECLASS}\shell\${VERB}" "" `${COMMANDTEXT}`
  WriteRegStr HKCR "${FILECLASS}\shell\${VERB}\command" "" `${COMMAND}`
!macroend
 
!macro APP_ASSOCIATE_REMOVEVERB FILECLASS VERB
  DeleteRegKey HKCR `${FILECLASS}\shell\${VERB}`
!macroend
 
 
!macro APP_UNASSOCIATE EXT FILECLASS
  ; Backup the previously associated file class
  ReadRegStr $R0 HKCR ".${EXT}" `${FILECLASS}_backup`
  WriteRegStr HKCR ".${EXT}" "" "$R0"
 
  DeleteRegKey HKCR `${FILECLASS}`
!macroend
 
!macro APP_ASSOCIATE_GETFILECLASS OUTPUT EXT
  ReadRegStr ${OUTPUT} HKCR ".${EXT}" ""
!macroend
 
 
; !defines for use with SHChangeNotify
!ifdef SHCNE_ASSOCCHANGED
!undef SHCNE_ASSOCCHANGED
!endif
!define SHCNE_ASSOCCHANGED 0x08000000
!ifdef SHCNF_FLUSH
!undef SHCNF_FLUSH
!endif
!define SHCNF_FLUSH        0x1000
 
!macro UPDATEFILEASSOC
; Using the system.dll plugin to call the SHChangeNotify Win32 API function so we
; can update the shell.
  System::Call "shell32::SHChangeNotify(i,i,i,i) (${SHCNE_ASSOCCHANGED}, ${SHCNF_FLUSH}, 0, 0)"
!macroend
 
;EOF
