## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount }초
scheduling-answer-button-time-minutes = { $amount }분
scheduling-answer-button-time-hours = { $amount }시간
scheduling-answer-button-time-days = { $amount }일
scheduling-answer-button-time-months = { $amount }달
scheduling-answer-button-time-years = { $amount }년

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds = { $amount }초
scheduling-time-span-minutes = { $amount }분
scheduling-time-span-hours = { $amount }시간
scheduling-time-span-days = { $amount }일
scheduling-time-span-months = { $amount }개월
scheduling-time-span-years = { $amount }년

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    다음 학습 카드는 { $unit ->
        [seconds] { $amount }초
        [minutes] { $amount }분
       *[hours] { $amount }시간
    } 뒤에 준비될 예정입니다.
scheduling-learn-remaining = 오늘 해야할 남은 카드는 { $remaining }개입니다.
scheduling-congratulations-finished = 축하합니다! 이 덱에서 해야하는 공부를 모두 마쳤습니다.
scheduling-today-review-limit-reached =
    복습을 기다리는 카드가 더 있지만, 일일 제한량에
    도달했습니다. 최적의 암기 효과를 위해, 옵션에서
    제한량을 높이길 권합니다.
scheduling-today-new-limit-reached =
    공부할 수 있는 새 카드가 더 있지만, 일일 제한량에
    도달했습니다. 옵션에서 제한량을 높일 수 있지만,
    새 카드를 더 많이 시작할수록 그에 따라 단기 복습량도
    늘어난다는 것을 주의하세요.
scheduling-buried-cards-found = 하나 이상의 카드가 미뤄졌으며 내일 표시될 예정입니다. 즉시 보고 싶다면 { $unburyThem }할 수 있습니다.
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = 미루기 해제
scheduling-how-to-custom-study = 정규 일정 외의 시간에 공부하려면 { $customStudy } 기능을 사용할 수 있습니다.
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = 맞춤 공부

## Scheduler upgrade

scheduling-update-soon = Anki 2.1은 과거 Anki 버전에서 발생했던 여러 문제를 해결하는 새로운 스케줄러와 함께 제공됩니다. 업데이트하는 걸 추천드립니다.
scheduling-update-done = 스케줄러가 성공적으로 업데이트되었습니다.
scheduling-update-button = 업데이트
scheduling-update-later-button = 나중에
scheduling-update-more-info-button = 더 알아보기
scheduling-update-required =
    컬렉션을 V2 스케줄러로 업그레이드해야 합니다.
    계속하기 전에 { scheduling-update-more-info-button }을 누르세요.

## Other scheduling strings

scheduling-always-include-question-side-when-replaying = 오디오 재생시 질문 부분을 항상 포함
scheduling-at-least-one-step-is-required = 적어도 하나의 단계는 반드시 필요합니다.
scheduling-automatically-play-audio = 자동으로 오디오 재생
scheduling-bury-related-new-cards-until-the = 연관된 새 카드를 다음날까지 미루기
scheduling-bury-related-reviews-until-the-next = 연관된 복습 카드를 다음날까지 미루기
scheduling-days = 일
scheduling-description = 설명
scheduling-easy-bonus = 쉬움 보너스
scheduling-easy-interval = 쉬움 복습간격
scheduling-end = (종료)
scheduling-general = 일반
scheduling-graduating-interval = 졸업 복습간격
scheduling-hard-interval = 어려움 복습간격
scheduling-ignore-answer-times-longer-than = 답변 시간이 너무 길 경우 무시할 기준:
scheduling-interval-modifier = 복습주기 조정값
scheduling-lapses = 실패
scheduling-lapses2 = 실패
scheduling-learning = 학습 카드
scheduling-leech-action = Leech 처리
scheduling-leech-threshold = Leech 기준
scheduling-maximum-interval = 최대 복습주기
scheduling-maximum-reviewsday = 최대 복습량/일
scheduling-minimum-interval = 최소 복습주기
scheduling-mix-new-cards-and-reviews = 새 카드와 복습 카드 섞기
scheduling-new-cards = 새 카드
scheduling-new-cardsday = 새 카드/일
scheduling-new-interval = 새 복습주기
scheduling-new-options-group-name = 새 옵션 그룹 이름:
scheduling-options-group = 옵션 그룹:
scheduling-order = 순서
scheduling-parent-limit = (부모 제한: { $val })
scheduling-reset-counts = 반복 및 실패 횟수 초기화
scheduling-restore-position = 가능하면 원래 위치로
scheduling-review = 복습
scheduling-reviews = 복습
scheduling-seconds = 초
scheduling-set-all-decks-below-to = { $val }의 모든 하위 덱에도 이 옵션 그룹을 지정하시겠습니까?
scheduling-set-for-all-subdecks = 모든 하위 덱에도 적용
scheduling-show-answer-timer = 답변 시간 표시
scheduling-show-new-cards-after-reviews = 새 카드는 복습 카드보다 나중에 등장
scheduling-show-new-cards-before-reviews = 새 카드는 복습 카드보다 먼저 등장
scheduling-show-new-cards-in-order-added = 추가한 순서대로 새 카드 공부
scheduling-show-new-cards-in-random-order = 무작위 순서로 새 카드 공부
scheduling-starting-ease = 초기 ease
scheduling-steps-in-minutes = 학습 단계(분 단위)
scheduling-steps-must-be-numbers = 학습 단계는 반드시 숫자로 지정하세요.
scheduling-tag-only = 태그만 달기
scheduling-the-default-configuration-cant-be-removed = 기본 설정은 삭제할 수 없습니다.
scheduling-your-changes-will-affect-multiple-decks = 변경사항이 여러 덱에 영향을 미칩니다. 현재 덱만 바꾸시려면, 새로운 옵션 그룹부터 만드세요.
scheduling-deck-updated = 덱 { $count }개를 업데이트했습니다.
scheduling-set-due-date-prompt = 카드를 며칠 후에 보시겠습니까?
scheduling-set-due-date-prompt-hint =
    0 = 오늘
    1! = 내일 + 주기를 1로 변경
    3-7 = 3~7일 중 임의로 선택
scheduling-set-due-date-done = 카드 { $cards }개의 만기 설정
scheduling-forgot-cards = 카드 { $cards }개를 초기화했습니다.
