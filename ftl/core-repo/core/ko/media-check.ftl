## Shown at the top of the media check screen

media-check-window-title = 미디어 검사
# the number of files, and the total space used by files
# that have been moved to the trash folder. eg,
# "Trash folder: 3 files, 3.47MB"
media-check-trash-count = 휴지통: 파일 { $count }개, { $megs }MB
media-check-missing-count = 잃어버린 파일: { $count }개
media-check-unused-count = 사용되지 않은 파일: { $count }개
media-check-renamed-count = 이름이 변경된 파일: { $count }개
media-check-oversize-count = 100MB 이상: { $count }개
media-check-subfolder-count = 서브폴더: { $count }개
media-check-extracted-count = 추출된 이미지: { $count }개

## Shown at the top of each section

media-check-renamed-header = 호환성을 위해 일부 파일의 이름을 바꿨습니다.
media-check-oversize-header = 100MB 이상인 파일은 AnkiWeb에 동기화할 수 없음.
media-check-subfolder-header = 미디어 폴더 속 하위 폴더는 지원하지 않습니다.
media-check-missing-header = 카드에 사용되었지만, 미디어 폴더에 없는 파일:
media-check-unused-header = 다음 파일은 미디어 폴더에 있지만 카드에 사용되고 있지는 않습니다.
media-check-template-references-field-header =
    미디어/LaTeX 태그에서 { "{{Field}}" } 참조를 사용하면 Anki는 사용된 파일인지 감지할 수 없습니다. 대신 미디어/LaTeX 태그를 개별 노트에 배치해야 합니다.
    
    참조할 템플릿:

## Shown once for each file

media-check-renamed-file = 이름 바뀜: { $old } -> { $new }
media-check-oversize-file = 100MB 이상: { $filename }
media-check-subfolder-file = 폴더: { $filename }
media-check-missing-file = 잃어버림: { $filename }
media-check-unused-file = 사용 안 됨: { $filename }

##

# Eg "Basic: Card 1 (Front Template)"
media-check-notetype-template = { $notetype }: { $card_type } ({ $side })

## Progress

media-check-checked = 체크됨 { $count }...

## Deleting unused media

media-check-delete-unused-confirm = 사용되지 않는 미디어 파일을 지울까요?
media-check-files-remaining = 파일 { $count }개 남았습니다.
media-check-delete-unused-complete =
    { $count ->
       *[other] { $count }개의 파일이
    } 휴지통으로 이동됨.
media-check-trash-emptied = 이제 쓰레기통이 비었습니다.
media-check-trash-restored = 삭제 파일을 미디어 폴더로 복구함.

## Rendering LaTeX

media-check-all-latex-rendered = 모든 LaTeX가 렌더링 됨.

## Buttons

media-check-delete-unused = Delete Unused
media-check-render-latex = LaTeX 렌더링
# button to permanently delete media files from the trash folder
media-check-empty-trash = Empty Trash
# button to move deleted files from the trash back into the media folder
media-check-restore-trash = 삭제 파일 복구
media-check-check-media-action = 미디어 검사
# a tag for notes with missing media files (must not contain whitespace)
media-check-missing-media-tag = 찾을 수 없는 미디어
# add a tag to notes with missing media
media-check-add-tag = 태그 없음
