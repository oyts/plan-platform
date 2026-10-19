; Application identity metadata only. Never enable notifications or alter OS policy.
!macro NSIS_HOOK_POSTINSTALL
  WriteRegStr HKCU "Software\Classes\AppUserModelId\${BUNDLEID}" "DisplayName" "${PRODUCTNAME}"
!macroend

!macro NSIS_HOOK_PREUNINSTALL
  ; Clear only this application's device authorization/task before removing its executable.
  ExecWait '"$INSTDIR\${MAINBINARYNAME}.exe" --reminder-clear'
!macroend

!macro NSIS_HOOK_POSTUNINSTALL
  DeleteRegKey HKCU "Software\Classes\AppUserModelId\${BUNDLEID}"
!macroend
