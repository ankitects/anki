## Errors shown when invalid search input is encountered.
## Backticks change the text formatting, so please don't change the backticks.
## Text inside backticks should not be changed unless noted.
## It's ok to change quotes outside of backticks however, eg:
## "`{ $context }`" => 「`{ $context }`」

search-invalid-search = 검색이 잘못되었습니다. { $reason }
search-misplaced-and = `and`가 발견되었지만 2개의 검색어가 연결되지 않습니다. 단어 자체를 검색하려면 큰따옴표로 묶어서 `"and"`로 검색하세요.
search-misplaced-or = `or`가 발견되었지만 2개의 검색어가 연결되지 않습니다. 단어 자체를 검색하려면 큰따옴표로 묶어서 `"or"`로 검색하세요.
# Here, the ellipsis "..." may be localised.
search-empty-group = 그룹 `(...)`를 찾았지만 대괄호 사이에 검색할 항목이 없습니다. 대괄호 자체를 검색하려면 큰따옴표로 묶어서 `"( )"`로 검색하세요.
search-unopened-group = 닫는 대괄호 `)`가 발견되었지만 그 앞에 여는 대괄호 `(`는 없습니다. `)` 자체를 검색하려면 큰따옴표로 묶어서 `")"`로 또는 백슬래시를 추가해서 `\)`로 검색하세요.
search-unclosed-group = 여는 대괄호 `(`가 발견되었지만 그 뒤에 닫는 대괄호 `)`는 없습니다. `(` 자체를 검색하려면 큰따옴표로 묶어서 `"("`로 또는 백슬래시를 추가해서 `\(`로 검색하세요.
search-empty-quote = 큰따옴표 `""` 쌍이 발견되었지만 둘 사이에 검색할 항목이 없습니다. 큰따옴표 자체를 검색하려면 백슬래시를 추가해서 `\"\"`로 검색하세요.
search-unclosed-quote = 여는 큰따옴표 `"`가 발견되었지만 닫는 두 번째 따옴표가 없습니다. `"` 자체를 검색하려면 백슬래시를 추가해서 `\"`로 검색하세요.
search-missing-key = 콜론 `:`를 찾았지만 앞에 키워드가 없습니다. `:` 자체를 검색하려면 백슬래시를 추가해서 `\:`로 검색하세요.
search-unknown-escape = 확장열 `{ $val }`는 정의되지 않았습니다. `\` 자체를 검색하려면 다른 백슬래시를 추가해서 `\\`로 검색하세요.
search-invalid-argument = `{ $term }`에 '`{ $argument }`'라는 잘못된 인수가 지정되었습니다.
search-invalid-flag-2 = `flag:` 뒤에는 다음과 같이 올바른 플래그 번호가 와야 합니다. `1` (빨강), `2` (노랑), `3` (초록), `4` (파랑), `5` (분홍), `6` (청록), `7` (노랑) 또는 `0`(해당 없음)
search-invalid-prop-operator = `prop:{ $val }` 뒤에는 다음 비교 연산자 중 하나가 와야 합니다. `=`, `!=`, `<`, `>`, `<=` 또는 `>=`
search-invalid-other = 오타가 있는지 확인해주세요.

## eg. expected a number in "due>5x", but found "5x"

search-invalid-number = "`{ $context }`"에서 숫자를 예상했지만 "`{ $provided }`"을 찾았습니다.
search-invalid-whole-number = "`{ $context }`"에서 범자연수를 예상했지만 "`{ $provided }`"을 찾았습니다.
search-invalid-positive-whole-number = "`{ $context }`"에서 양수를 예상했지만 "`{ $provided }`"을 찾았습니다.
search-invalid-negative-whole-number = "`{ $context }`"에서 0을 포함한 음수를 예상했지만 "`{ $provided }`"을 찾았습니다.
search-invalid-answer-button = "`{ $context }`"에서 1~4 사이의 답변 버튼을 예상했지만 "`{ $provided }`"을 찾았습니다.

## Column labels in browse screen

search-note-modified = 노트 수정됨
search-card-modified = 카드 수정됨

##

# Tooltip for search lines outside browser
search-view-in-browser = 브라우저에서 보기
