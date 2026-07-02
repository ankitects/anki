importing-failed-debug-info = 가져오기 실패. 디버깅 정보:
importing-aborted = 중단됨: { $val }
importing-added-duplicate-with-first-field = 정렬 필드와 중복된 노트 추가: { $val }
importing-all-supported-formats = 지원하는 모든 형식 { $val }
importing-allow-html-in-fields = 필드 안에 HTML 허용
importing-anki-files-are-from-a-very = .anki 파일은 이전 버전의 Anki에서 만든 것입니다.  Anki 2.0으로 해당 파일을 불러올 수 있습니다.
importing-anki2-files-are-not-directly-importable = .anki2 파일은 직접 불러올수 없습니다. .apkg 또는 .zip 파일로 불러오기 바랍니다.
importing-appeared-twice-in-file = 파일에서 두 번 등장합니다: { $val }
importing-by-default-anki-will-detect-the = 기본적으로 Anki는 탭이나 쉼표 같은 필드 구분 문자를 자동으로 감지합니다. 만약 Anki가 구분 문자를 제대로 감지하지 못한다면, 이곳에 구분 문자를 직접 입력하세요. 탭은 \t로 표현하세요.
importing-cannot-merge-notetypes-of-different-kinds =
    Cloze 형식의 노트를 기존 형식의 노트와 합병할 수 없습니다.
    '{ importing-merge-notetypes }' 설정이 비활성화 된 상태로 파일을 가져올 수 있습니다.
importing-change = 수정
importing-colon = 쌍점
importing-comma = 쉼표
importing-empty-first-field = 비어 있는 첫 필드: { $val }
importing-field-separator = 필드 구분 기호
importing-field-mapping = 필드 배정
importing-field-of-file-is = 필드 <b>{ $val }</b:
importing-fields-separated-by = 필드 구분자: { $val }
importing-file-must-contain-field-column = 파일에는 노트 필드에 매핑할 수 있는 열이 하나 이상 포함되어 있어야 합니다.
importing-file-version-unknown-trying-import-anyway = 알수없는 버전의 파일을 어찌됐든 가져오기
importing-first-field-matched = 정렬 필드 일치: { $val }
importing-identical = 동일
importing-ignore-field = 필드 무시
importing-ignore-lines-where-first-field-matches = 기존 노트와 첫째 필드가 일치하는 줄은 무시하기
importing-ignored = <무시>
importing-import-even-if-existing-note-has = 기존 노트와 첫째 필드가 같더라도 가져오기
importing-import-options = 가져오기 옵션
importing-importing-complete = 가져오기 완료.
importing-invalid-file-please-restore-from-backup = 유효하지 않은 파일. 백업에서 복구하세요.
importing-map-to = { $val }로 배정
importing-map-to-tags = 태그로 배정
importing-mapped-to = <b>{ $val }</b>에 배정됨
importing-mapped-to-tags = <b>태그</b>로 배정됨
# the action of combining two existing note types to create a new one
importing-merge-notetypes = 노트 타입 병합
importing-merge-notetypes-help =
    체크 시, 만약 당신이나 덱 작성자가 노트 유형의 스키마를 변경했다면, Anki는 두 버전을 둘 다 유지하지 않고 병합합니다.
    노트 유형의 스키마 변경이란, 필드나 템플릿을 추가하거나 제거하거나 재정렬하거나, 정렬 필드를 변경하는 것을 의미합니다.
    반면, 기존 템플릿의 앞면을 변경하는 것은 스키마 변경에 포함되지 않습니다.
    
    경고: 이는 단방향 동기화가 필요하며, 기존 노트가 수정된 것으로 표시될 수 있습니다.
importing-mnemosyne-20-deck-db = Mnemosyne 2.0 덱 (*.db)
importing-multicharacter-separators-are-not-supported-please = 여러 글자의 분리 기호를 지원하지 않습니다. 하나의 분리 기호만 사용해주세요.
importing-notes-added-from-file = 파일에서 노트를 추가함 : { $val }
importing-notes-found-in-file = 파일에서 노트를 발견함 : { $val }
importing-notes-skipped-as-theyre-already-in = 이미 컬렉션에 있기 때문에 노트를 추가하지 않았습니다 : { $val }
importing-notes-skipped-update-due-to-notetype = 노트 유형이 처음 노트를 가져온 이후 변경되었으므로, 노트가 업데이트되지 않았습니다: { $val }
importing-notes-updated-as-file-had-newer = 파일에 더 최신 버전이 있어, 노트가 업데이트되었습니다: { $val }
importing-include-reviews = 복습 포함
importing-also-import-progress = 학습 진행 상태 가져오기
importing-with-deck-configs = 덱 사전 설정 가져오기
importing-updates = 덮어쓰기
importing-include-reviews-help =
    이 옵션이 활성화되면, 덱 공유자가 포함한 이전 복습 기록도 함께 가져옵니다.
    비활성화되면, 모든 카드는 새로운 카드로 가져오며, “leech” 또는 “marked” 태그는 제거됩니다.
importing-with-deck-configs-help =
    이 옵션이 활성화되면, 덱 공유자가 포함한 덱 옵션도 함께 가져옵니다.
    비활성화되면, 모든 덱은 기본 사전 설정이 적용됩니다.
importing-packaged-anki-deckcollection-apkg-colpkg-zip = 압축된 Anki 덱/컬렉션 (*.apkg *.colpkg *.zip)
importing-pauker-18-lesson-paugz = Pauker 1.8 Lesson (*.pau.gz)
# the '|' character
importing-pipe = 수직선
# Warning displayed when the csv import preview table is clipped (some columns were hidden)
# $count is intended to be a large number (1000 and above)
importing-preview-truncated = 첫 번째 { $count }개의 열만 표시됩니다. 이 내용이 맞지 않으면, 필드 구분자를 변경해 보세요.
importing-rows-had-num1d-fields-expected-num2d = '{ $row }'째 줄의 필드는 { $found }개. 예상한 필드는 { $expected }개.
importing-selected-file-was-not-in-utf8 = 선택한 파일이 UTF-8 형식이 아닙니다. 매뉴얼의 가져오기 부분을 참고해 주세요.
importing-semicolon = 쌍반점
importing-skipped = 건너뜀
importing-supermemo-xml-export-xml = Supermemo XML (*.xml)
importing-tab = 탭
importing-tag-modified-notes = 수정된 노트 태그
importing-text-separated-by-tabs-or-semicolons = 탭이나 쌍반점으로 구분한 텍스트 파일 (*)
importing-the-first-field-of-the-note = 노트 유형의 첫 필드는 반드시 배정되야합니다.
importing-the-provided-file-is-not-a = 유효한 .apkg 파일이 아닙니다.
importing-this-file-does-not-appear-to = 유효한 .apkg 파일이 아닙니다. AnkiWeb에서 다운로드한 파일이라면 다운로드에 실패했을 가능성이 있습니다. 다시 시도해 보시고, 문제가 계속되면 다른 브라우저로 다시 시도해 주세요.
importing-this-will-delete-your-existing-collection = 기존의 컬렉션을 모두 삭제하고 가져오기하는 파일에 들어있는 데이터로 대체합니다. 계속 진행할까요?
importing-unable-to-import-from-a-readonly = 읽기 전용 파일은 가져올 수 없습니다.
importing-unknown-file-format = 알 수 없는 파일 형식.
importing-update-existing-notes-when-first-field = 첫 필드가 일치할 경우 기존의 노트를 업데이트
importing-updated = 업데이트됨
importing-update-always = 항상
importing-update-notes = 노트 업데이트
importing-update-notes-help =
    기존 노트를 언제 업데이트할지 설정하는 옵션입니다. 기본적으로, 이는
    일치하는 가져온 노트가 더 최근에 수정된 경우에만 수행됩니다.
importing-update-notetypes = 노트 유형 업데이트
importing-update-notetypes-help =
    기존 노트 유형을 언제 업데이트할지 설정하는 옵션입니다.
    기본적으로, 이는 일치하는 가져온 노트 유형이 더 최근에 수정된 경우에만 수행됩니다.
    템플릿 텍스트와 스타일링 변경은 항상 가져올 수 있지만,
    스키마 변경(예: 필드의 수나 순서 변경)이 있을 경우에는 ‘{ importing-merge-notetypes }’ 옵션을 활성화해야 합니다.
importing-note-added =
    { $count ->
       *[other] { $count }노트를 추가했습니다.
    }
importing-note-imported =
    { $count ->
       *[other] 노트  { $count }개를 가져왔습니다.
    }
importing-note-unchanged =
    { $count ->
       *[other] 노트 { $count }개는 변경되지 않았습니다.
    }
importing-note-updated =
    { $count ->
       *[other] { $count }노트를 업데이트했습니다.
    }
importing-processed-media-file =
    { $count ->
       *[other] { $count }개의 미디어 파일을 처리함
    }
importing-importing-file = 파일 가져오는 중...
importing-extracting = 데이터 추출하는 중...
importing-gathering = 데이터 수집하는 중...
importing-failed-to-import-media-file = 미디어 파일을 가져오는 데 실패했습니다. { $debuginfo }
importing-processed-notes = 노트 { $count }개 처리 완료...
importing-processed-cards = 카드 { $count }개 처리 완료...
importing-existing-notes = 기존 노트
# "Existing notes: Duplicate" (verb)
importing-duplicate = 중복 허용
# "Existing notes: Preserve" (verb)
importing-preserve = 기존 그대로
# "Existing notes: Update" (verb)
importing-update = 덮어쓰기
importing-tag-all-notes = 모든 노트 태그
importing-tag-updated-notes = 덮어쓴 노트 태그
importing-file = 파일
importing-cards-added = 카드 { $count }개를 추가했습니다.
importing-file-empty = 선택한 파일이 비어있습니다.
importing-notes-added = 노트 { $count }개를 가져왔습니다.
importing-notes-updated = 기존 노트 { $count }개를 업데이트 했습니다.
importing-notes-failed = 노트 { $count }개를 가져올 수 없습니다.
importing-conflicting-notes-skipped = 노트 { $count }개를 가져올 수 없습니다. 노트 타입이 변경되어 있습니다.
importing-conflicting-notes-skipped2 = 노트 { $count }개를 가져올 수 없습니다. 노트 타입이 변경되어 있으며, '{ importing-merge-notetypes }'가 활성화되어 있지 않습니다.
importing-import-log = 가져오기 로그
importing-no-notes-in-file = 이 파일에서 노트를 찾을 수 없습니다.
importing-show = 보이기
importing-status = 상태
importing-added-new-note = 새 노트 추가됨
importing-note-updated-as-file-had-newer = 파일의 최신 버전으로, 노트가 업데이트 되었습니다.
importing-note-skipped-due-to-missing-notetype = 노트 타입을 찾을 수 없어, 노트를 건너 뛰었습니다.
importing-note-skipped-due-to-missing-deck = 덱이 없어, 노트를 건너 뛰었습니다.
importing-note-skipped-due-to-empty-first-field = 첫 번째 필드가 비어 있어 노트를 건너뛰었습니다.
importing-field-separator-help =
    텍스트 파일에서 필드를 구분하는 문자입니다.
    미리보기를 사용하여 필드가 올바르게 구분되었는지 확인할 수 있습니다.
    
    이 문자가 필드 안에 포함되어 있으면, 해당 필드는 CSV 표준에 따라 적절히 따옴표로 묶어야 합니다.
    LibreOffice와 같은 스프레드시트 프로그램은 이를 자동으로 처리합니다.
importing-allow-html-in-fields-help =
    파일에 HTML 포맷이 있을 경우, 이 설정을 활성화하세요.
    예를 들어, 파일에 '&lt;br&gt;' 문자열이 있을 경우, 카드에서 줄 바꿈으로 표시됩니다.
    비활성화 할 경우, 문자 그대로 '&lt;br&gt;'가 표시됩니다.
importing-notetype-help =
    새로 가져온 노트는 이 노트 유형을 사용하게 되며,
    기존 노트는 이 노트 유형이 있을 경우에만 업데이트됩니다.
    
    매핑 도구를 사용하여 파일의 어떤 필드가
    어떤 노트 유형 필드에 해당하는지 선택할 수 있습니다.
importing-deck-help = 가져온 카드는 이 덱에 저장됩니다.

## NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.

importing-importing-collection = 컬렉션 가져오는 중...
importing-unable-to-import-filename = { $filename }을 가져오지 못했습니다. 지원하지 않는 파일 유형입니다.
importing-notes-that-could-not-be-imported = 노트 타입이 바뀌었기 때문에 불러올 수 없는 노트 : { $val }
importing-added = 추가됨
