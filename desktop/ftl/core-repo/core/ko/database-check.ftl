database-check-corrupt = 컬렉션 파일이 손상되었습니다. 자동으로 백업된 파일에서 복구하세요.
database-check-rebuilt = 데이터베이스를 재구성하고 최적화했습니다.
database-check-card-properties = 잘못된 설정을 가진 카드 { $count }개를 수정했습니다.
database-check-missing-templates = 서식이 사라진 { $count }카드를 삭제했습니다.
database-check-field-count =
    { $count ->
        [one] 필드가 잘못된 노트 { $count }개를 삭제했습니다.
       *[other] 필드가 잘못된 노트 { $count }개를 삭제했습니다.
    }
database-check-new-card-high-due =
    { $count ->
       *[other] 만기 순서가 100만 이상인 { $count }개의 카드를 찾았습니다. 탐색 메뉴에서 순서를 앞당기는 것을 고려해보세요.
    }
database-check-card-missing-note = 노트가 사라진 { $count }카드를 삭제했습니다.
database-check-duplicate-card-ords =
    { $count ->
       *[other] 템플릿이 중복된 카드 { $count }개를 삭제했습니다.
    }
database-check-missing-decks = 누락된 덱 { $count }개를 고쳤습니다.
database-check-revlog-properties =
    { $count ->
       *[other] 잘못된 설정을 가진 복습 기록 { $count }개를 고쳤습니다.
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
       *[other] 잘못된 utf-8 문자를 쓴 노트 { $count }개를 고쳤습니다.
    }
# "db-check" is always in English
database-check-notetypes-recovered = 지정된 노트 유형을 찾을 수 없습니다. 일단 노트에 "db-check"로 시작하는 새 노트 유형을 지정했지만, 필드 이름이나 카드 디자인이 없어졌기 때문에 자동 백업에서 복원하는 게 더 나을 수 있습니다.

## Progress info

database-check-checking-integrity = 컬렉션 확인 중...
database-check-rebuilding = 재구축 중...
database-check-checking-cards = 카드 확인 중...
database-check-checking-notes = 노트 확인 중...
database-check-checking-history = 기록 확인 중...
database-check-title = 데이터베이스 확인
