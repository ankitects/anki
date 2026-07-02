### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = 추가: { $up }↑ { $down }↓
sync-media-removed-count = 삭제:{ $up }↑ { $down }↓
sync-media-checked-count = 확인됨: { $count }
sync-media-starting = 미디어 동기화 시작 중...
sync-media-complete = 미디어 동기화 완료.
sync-media-failed = 미디어 동기화 실패.
sync-media-aborting = 미디어 동기화 중단 중...
sync-media-aborted = 미디어 동기화 중단됨.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = 미디어 동기화 비활성화됨.
# Title of the screen that shows syncing progress history
sync-media-log-title = 미디어 동기화 기록

## Error messages / dialogs

sync-conflict = 한 번에 Anki 하나만 계정에 동기화할 수 있습니다. 잠시 기다린 후 다시 시도하십시오.
sync-server-error = AnkiWeb에 문제가 발생하였습니다. 잠시 후 다시 시도하십시오.
sync-client-too-old = Anki 버전이 너무 오래되었습니다. 동기화를 계속하려면 최신 버전으로 업데이트해 주십시오.
sync-wrong-pass = AnkiWeb 아이디나 비밀번호가 틀렸습니다. 다시 시도하세요.
sync-resync-required = 다시 동기화하십시오. 이 메시지가 계속 나타나면 지원 사이트에 게시하십시오.
sync-must-wait-for-end = Anki가 동기화 중입니다. 동기화가 끝날 때까지 기다린 뒤 다시 시도하십시오.
sync-confirm-empty-download = 로컬의 컬렉션에는 카드가 존재하지 않습니다. AnkiWebAnkiweb에서 다운로드할까요?
sync-confirm-empty-upload = AnkiWeb 컬렉션에 카드가 없습니다. 로컬 컬렉션으로 대체하시겠습니까?
sync-conflict-explanation =
    이 기기에 저장된 내용과 AnkiWeb에 저장된 내용이 차이가 나기 때문에, 한쪽의 내용을 다른 쪽으로 덮어써야만 합니다.
    
    AnkiWeb의 내용을 이 컴퓨터에 덮어쓰려면 'AnkiWeb에서 다운로드'를 선택해 주세요. (주의: 마지막 동기화 이후 이 컴퓨터에서 이루어진 변경 사항은 소실됩니다)
    이 컴퓨터의 내용을 AnkiWeb에 덮어쓰려면 'AnkiWeb으로 업로드'를 선택해 주세요. (주의: 마지막 동기화 이후 AnkiWeb이나 다른 기기에서 이루어진 변경 사항은 소실됩니다.)
    
    모든 기기들이 동기화되고 나면, 이후의 복습 내용과 추가된 카드는 자동적으로 처리될 수 있습니다.
sync-conflict-explanation2 =
    현재 이 기기의 덱과 AnkiWeb 간에 충돌이 발생했습니다. 유지할 버전을 선택해야합니다:
    
    - { sync-download-from-ankiweb }의 경우, 이 기기의 덱을 AnkiWeb의 버전으로 덮어씁니다. 마지막 동기화 이후 이 기기에서 변경된 사항은 모두 사라집니다.
    - { sync-upload-to-ankiweb }의 경우, 이 기기의 덱을 AnkiWeb으로 업로드하며, AnkiWeb의 변경 사항은 삭제됩니다.
    
    충돌이 해결되면, 이후 동기화는 정상적으로 작동합니다.
sync-ankiweb-id-label = AnkiWeb 아이디:
sync-password-label = 비밀번호:
sync-account-required =
    <h1>계정이 필요합니다</h1>
    무료 계정이 있어야 사용자의 컬렉션을 동기화할 수 있습니다. <a href="{ $link }">사용자 등록</a>을 한 뒤, 필요한 정보를 아래에 입력하세요.
sync-sanity-check-failed = 데이터베이스 확인 기능을 사용한 다음 다시 동기화하십시오. 문제가 지속되면 기본 설정 화면에서 전체 동기화를 수행하십시오.
sync-clock-off = 동기화할 수 없음 - 시계가 올바른 시간으로 설정되지 않음.
sync-upload-too-large =
    컬렉션 파일이 너무 커서 AnkiWeb으로 전송할 수 없습니다. 파일의
    크기를 줄이려면 필요없는 덱을 (선택적으로 먼저 내보내기한 후) 삭제한 다음,
    데이터베이스 체크하기를 통해 파일 크기를 줄일 수 있습니다. ({ $details })
sync-sign-in = 로그인
sync-ankihub-dialog-heading = AnkiHub 로그인
sync-ankihub-username-label = 사용자 이름 또는 이메일
sync-ankihub-login-failed = 해당 계정으로 AnkiHub에 로그인할 수 없습니다.
sync-ankihub-addon-installation = AnkiHub 애드온(add-on) 설치

## Buttons

sync-media-log-button = 미디어 기록
sync-abort-button = 중단
sync-download-from-ankiweb = AnkiWeb에서 다운로드
sync-upload-to-ankiweb = AnkiWeb으로 업로드
sync-cancel-button = 취소

## Normal sync progress

sync-downloading-from-ankiweb = AnkiWeb에서 다운로드하는 중...
sync-uploading-to-ankiweb = AnkiWeb에 업로드 중...
sync-syncing = 동기화 중...
sync-checking = 검사 중...
sync-connecting = 연결 중...
sync-added-updated-count = 추가/수정: { $up }↑ { $down }↓
sync-log-in-button = 로그인
sync-log-out-button = 로그아웃
sync-collection-complete = 컬렉션 동기화가 완료되었습니다.
