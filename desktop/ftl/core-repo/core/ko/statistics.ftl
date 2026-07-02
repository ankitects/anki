# The date a card will be ready to review
statistics-due-date = 만기
# The count of cards waiting to be reviewed
statistics-due-count = 만기
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = 신규 카드 #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } 카드/분
statistics-average-answer-time = { $average-seconds } 초 ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds = { $amount }초 동안
statistics-in-time-span-minutes = { $amount }분 동안
statistics-in-time-span-hours = { $amount }시간 동안
statistics-in-time-span-days = { $amount }일 동안
statistics-in-time-span-months = { $amount }달 동안
statistics-in-time-span-years = { $amount }년 동안
# Shown at the bottom of the deck list, and in the statistics screen.
# eg "Studied 3 cards in 13 seconds today (4.33s/card)."
# The { statistics-in-time-span-seconds } part should be pasted in from the English
# version unmodified.
statistics-studied-today =
    공부 { statistics-cards } { $unit ->
        [seconds] { statistics-in-time-span-seconds }
        [minutes] { statistics-in-time-span-minutes }
        [hours] { statistics-in-time-span-hours }
        [days] { statistics-in-time-span-days }
        [months] { statistics-in-time-span-months }
       *[years] { statistics-in-time-span-years }
    } 오늘({ $secs-per-card }s/card)

##

statistics-cards = { $cards }카드
statistics-notes = 노트
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews = { $reviews }복습
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = { $memorized } 기억함
statistics-today-title = 오늘
statistics-today-again-count = '다시' 버튼 누른 횟수:
statistics-today-type-counts = 학습: { $learnCount }, 복습: { $reviewCount }, 재학습: { $relearnCount }, 필터됨:{ $filteredCount }
statistics-today-no-cards = 오늘 학습한 카드가 없습니다.
statistics-today-no-mature-cards = 오늘 학습한 성숙 카드가 없습니다.
statistics-today-correct-mature = 성숙 카드 정답률: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = 전체 카드
statistics-counts-new-cards = 새카드
statistics-counts-young-cards = 어린 카드
statistics-counts-mature-cards = 성숙 카드
statistics-counts-suspended-cards = 일시중단됨
statistics-counts-buried-cards = 미뤄짐
statistics-counts-filtered-cards = 필터링됨
statistics-counts-learning-cards = 학습 카드
statistics-counts-relearning-cards = 재학습 카드
statistics-counts-title = 카드 개수
statistics-counts-separate-suspended-buried-cards = 일시중단/미뤄진 카드 제외하기

## True Retention represents your actual retention rate from past reviews, in
## comparison to the "desired retention" parameter of FSRS, which forecasts
## future retention. True Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.

statistics-true-retention-range = 범위
statistics-true-retention-fail = 실패
# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = 전체 카드
statistics-true-retention-retention = 기억률
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = 어린 카드
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = 성숙 카드
statistics-true-retention-all = 전체
statistics-true-retention-today = 오늘
statistics-true-retention-yesterday = 어제
statistics-true-retention-week = 지난 주
statistics-true-retention-month = 지난 달
statistics-true-retention-year = 작년
statistics-true-retention-all-time = 전체
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = N/A

##

statistics-range-all-time = 전체
statistics-range-1-year-history = 최근 12달간
statistics-range-all-history = 모든 기록
statistics-range-deck = 덱
statistics-range-collection = 컬렉션
statistics-range-search = 찾기
statistics-card-ease-title = 카드 ease
statistics-card-difficulty-title = 카드 난이도
statistics-card-stability-title = 카드 안정성
statistics-card-stability-subtitle = 기억 확률이 90%가 될 때까지의 지연
statistics-average-stability = 평균 안정성
statistics-card-retrievability-title = 카드 기억 확률
statistics-card-ease-subtitle = ease가 낮을수록 카드가 더 자주 등장합니다.
statistics-card-difficulty-subtitle2 = 난이도가 높을수록, 안정성이 천천히 증가합니다.
statistics-retrievability-subtitle = 오늘의 카드 기억 확률
# eg "3 cards with 150-170% ease"
statistics-card-ease-tooltip =
    { $cards ->
       *[other] { $percent } ease의 { $cards }개 카드
    }
statistics-retrievability-tooltip = { $percent } 기억 확률의 { $cards }개 카드
statistics-future-due-title = 예상
statistics-future-due-subtitle = 앞으로 공부할 복습량
statistics-added-title = 추가됨
statistics-added-subtitle = 추가한 카드의 수
statistics-reviews-count-subtitle = 답변한 질문 수
statistics-reviews-time-subtitle = 질문에 답을 하기까지 걸린 시간
statistics-answer-buttons-title = 답 버튼
# eg Button: 4
statistics-answer-buttons-button-number = 버튼
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = 눌린 횟수
statistics-answer-buttons-subtitle = 각 버튼을 누른 횟수
statistics-reviews-title = 복습
statistics-reviews-time-checkbox = 시간
statistics-in-days-single =
    { $days ->
        [0] 오늘
        [1] 내일
       *[other] { $days }일 이내
    }
statistics-in-days-range = { $daysStart }-{ $daysEnd }일 뒤
statistics-days-ago-single =
    { $days ->
        [1] 어제
       *[other] { $days }일 전
    }
statistics-days-ago-range = { $daysStart }-{ $daysEnd }일 전
statistics-running-total = 총 카드
statistics-cards-due =
    { $cards ->
       *[other] { $cards }개 카드 예정됨
    }
statistics-backlog-checkbox = 기일 초과
statistics-intervals-title = 복습간격
statistics-intervals-subtitle = 복습카드가 다시 등장할 때까지의 간격
statistics-intervals-day-range =
    { $cards ->
       *[other] { $daysStart }~{ $daysEnd }일의 복습 주기를 가진 { $cards }개 카드
    }
statistics-intervals-day-single =
    { $cards ->
       *[other] { $day }일의 복습 주기를 가진 { $cards }개 카드
    }
statistics-stability-day-single = { $day }일의 안정성인 카드 { $cards }개
# hour range, eg "From 14:00-15:00"
statistics-hours-range = { $hourStart }:00~{ $hourEnd }:00
statistics-hours-correct = { $correct }/{ $total } 정답 ({ $percent }%)
statistics-hours-title = 시간대별 분석
statistics-hours-subtitle = 하루 중 시간대별 정답률
# shown when graph is empty
statistics-no-data = 데이터 없음
statistics-calendar-title = 달력

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount }초
statistics-elapsed-time-minutes = { $amount }분
statistics-elapsed-time-hours = { $amount }시간
statistics-elapsed-time-days = { $amount }일
statistics-elapsed-time-months = { $amount }달
statistics-elapsed-time-years = { $amount }년

##

statistics-average-for-days-studied = 공부기간 동안 평균
statistics-total = 전체
statistics-days-studied = 공부기간
statistics-average-answer-time-label = 평균 답변 시간
statistics-average = 평균
statistics-average-interval = 평균 복습간격
statistics-due-tomorrow = 내일 만기
# eg 5 of 15 (33.3%)
statistics-amount-of-total-with-percentage = { $total }개 중 { $amount }개 ({ $percent }%)
statistics-average-over-period = 전체기간 평균
statistics-reviews-per-day =
    { $count ->
       *[other] { $count } 복습/일
    }
statistics-minutes-per-day =
    { $count ->
       *[other] { $count }분/일
    }
statistics-cards-per-day =
    { $count ->
       *[other] { $count } 카드/일
    }
statistics-average-ease = 평균 ease
statistics-average-retrievability = 평균 카드 기억 확률
statistics-save-pdf = PDF로 저장
statistics-saved = 저장됨.
statistics-stats = 통계
