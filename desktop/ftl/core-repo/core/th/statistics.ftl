# The date a card will be ready to review
statistics-due-date = วันที่ครบกำหนด
# The count of cards waiting to be reviewed
statistics-due-count = การ์ดที่ครบกำหนด
# Shown in the Due column of the Browse screen when the card is a new card
statistics-due-for-new-card = ใหม่ #{ $number }

## eg 16.8s (3.6 cards/minute)

statistics-cards-per-min = { $cards-per-minute } การ์ด/นาที
statistics-average-answer-time = { $average-seconds } วินาที ({ statistics-cards-per-min })

## A span of time studying took place in, for example
## "(studied 30 cards) in 3 minutes"

statistics-in-time-span-seconds = ใน { $amount } วินาที
statistics-in-time-span-minutes = ใน { $amount } นาที
statistics-in-time-span-hours = ใน { $amount } ชั่วโมง
statistics-in-time-span-days = ใน { $amount } วัน
statistics-in-time-span-months = ใน { $amount } เดือน
statistics-in-time-span-years = ใน { $amount } ปี

##

statistics-cards = การ์ด { $cards } ใบ
statistics-notes = โน้ต { $notes } โน้ต
# a count of how many cards have been answered, eg "Total: 34 reviews"
statistics-reviews = ทบทวน { $reviews } รายการ
# This fragment of the tooltip in the FSRS simulation
# diagram (Deck options -> FSRS) shows the total number of
# cards that can be recalled or retrieved on a specific date.
statistics-memorized = จดจำการ์ด { $memorized } ใบแล้ว
statistics-today-title = วันนี้
statistics-today-again-count = จำนวนการตอบ "อีกครั้ง":
statistics-today-type-counts = เรียนรู้: { $learnCount }, ทบทวน: { $reviewCount }, เรียนรู้ซ้ำ: { $relearnCount }, กรอง: { $filteredCount }
statistics-today-no-cards = ไม่มีการ์ดที่ฝึกฝนวันนี้
statistics-today-no-mature-cards = ไม่มีการ์ดแก่กล้าที่ฝึกฝนวันนี้
statistics-today-correct-mature = อัตราการตอบการ์ดแก่กล้าถูก: { $correct }/{ $total } ({ $percent }%)
statistics-counts-total-cards = ทั้งหมด
statistics-counts-new-cards = ใหม่
statistics-counts-young-cards = แบเบาะ
statistics-counts-mature-cards = แก่กล้า
statistics-counts-suspended-cards = ถูกพักแล้ว
statistics-counts-buried-cards = ถูกซ่อนสำหรับวันนี้
statistics-counts-filtered-cards = กรองแล้ว
statistics-counts-learning-cards = กำลังเรียน
statistics-counts-relearning-cards = กำลังเรียนใหม่
statistics-counts-title = จำนวนการ์ด
statistics-counts-separate-suspended-buried-cards = แยกการ์ดที่ถูกพัก/ซ่อน

## Retention represents your actual retention from past reviews, in
## comparison to the "desired retention" setting of FSRS, which forecasts
## future retention. Retention is the percentage of all reviewed cards
## that were marked as "Hard," "Good," or "Easy" within a specific time period.
##
## Most of these strings are used as column / row headings in a table.
## (Excluding -title and -subtitle)
## It is important to keep these translations short so that they do not make
## the table too large to display on a single stats card.
##
## N.B. Stats cards may be very small on mobile devices and when the Stats
##      window is certain sizes.

# This will usually be the same as statistics-counts-total-cards
statistics-true-retention-total = ทั้งหมด
statistics-true-retention-count = จำนวน
# This will usually be the same as statistics-counts-young-cards
statistics-true-retention-young = แบเบาะ
# This will usually be the same as statistics-counts-mature-cards
statistics-true-retention-mature = แก่กล้า
statistics-true-retention-all = ทั้งหมด
statistics-true-retention-today = วันนี้
statistics-true-retention-yesterday = เมื่อวาน
statistics-true-retention-week = สัปดาห์ที่แล้ว
statistics-true-retention-month = เดือนที่แล้ว
statistics-true-retention-year = ปีที่แล้ว
statistics-true-retention-all-time = ตลอดกาล
# If there are no reviews within a specific time period, the retention
# percentage cannot be calculated and is displayed as "N/A."
statistics-true-retention-not-applicable = ไม่มีข้อมูล

##

statistics-range-1-year-history = 12 เดือนที่ผ่านมา
statistics-range-all-history = ประวัติทั้งหมด
statistics-range-deck = สำรับ
statistics-range-search = ค้นหา
statistics-card-ease-title = ค่าความง่ายในการจำการ์ด
statistics-card-difficulty-title = ความยากของการ์ด
statistics-card-stability-title = ความเสถียรของการ์ด
statistics-card-ease-subtitle = ยิ่งค่าความง่ายในการจำต่ำ การ์ดยิ่งปรากฏบ่อย
statistics-card-difficulty-subtitle2 = ความยากยิ่งสูง ความเสถียรยิ่งเพิ่มขึ้นช้า
statistics-card-difficulty-tooltip = มีการ์ด { $cards } ใบที่มีความยาก { $percent }
statistics-future-due-title = ครบกำหนดในอนาคต
statistics-future-due-subtitle = จำนวนการทบทวนที่จะครบกำหนดในอนาคต
statistics-added-title = เพิ่มแล้ว
statistics-added-subtitle = จำนวนการ์ดใหม่ที่ได้เพิ่มไปแล้ว
statistics-reviews-count-subtitle = จำนวนคำถามที่ได้ตอบไป
statistics-reviews-time-subtitle = ระยะเวลาที่ใช้ตอบคำถาม
statistics-answer-buttons-title = ปุ่มคำตอบ
# eg Button: 4
statistics-answer-buttons-button-number = ปุ่ม
# eg Times pressed: 123
statistics-answer-buttons-button-pressed = จำนวนครั้งที่กด
statistics-answer-buttons-subtitle = จำนวนครั้งที่กดแต่ละปุ่ม
statistics-reviews-title = ทบทวน
statistics-in-days-single =
    { $days ->
        [1] พรุ่งนี้
        [0] วันนี้
       *[other] ในอีก { $days } วัน
    }
statistics-days-ago-single =
    { $days ->
        [1] เมื่อวาน
       *[other] { $days } วันก่อน
    }
statistics-cards-due = มีการ์ด { $cards } ใบครบกำหนด
statistics-intervals-title = ระยะห่างระหว่างการทบทวน
statistics-hours-title = แยกตามชั่วโมง
# shown when graph is empty
statistics-no-data = ไม่มีข้อมูล
statistics-calendar-title = ปฏิทิน

## An amount of elapsed time, used in the graphs to show the amount of
## time spent studying. For example, English would show "5s" for 5 seconds,
## "13.5m" for 13.5 minutes, and so on.
##
## Please try to keep the text short, as longer text may get cut off.

statistics-elapsed-time-seconds = { $amount } วินาที
statistics-elapsed-time-minutes = { $amount } นาที
statistics-elapsed-time-hours = { $amount } ชั่วโมง
statistics-elapsed-time-days = { $amount } วัน
statistics-elapsed-time-months = { $amount } เดือน
statistics-elapsed-time-years = { $amount } ปี

##

statistics-average-for-days-studied = ค่าเฉลี่ยวันที่ฝึกฝน
# This term is used in a variety of contexts to refers to the total amount of
# items (e.g., cards, mature cards, etc) for a given period, rather than the
# total of all existing items.
statistics-total = ทั้งหมด
statistics-days-studied = จำนวนวันที่ฝึกฝน
statistics-average-answer-time-label = เวลาการตอบโดยเฉลี่ย
statistics-average = เฉลี่ย
statistics-due-tomorrow = ครบกำหนดพรุ่งนี้
statistics-reviews-per-day = ทบทวน { $count } ครั้ง/วัน
statistics-minutes-per-day = { $count } นาที/วัน
statistics-cards-per-day = { $count } การ์ด/วัน
statistics-estimated-total-knowledge = ความรู้ทั้งหมดที่ประมาณการ
statistics-save-pdf = บันทึก PDF
statistics-saved = บันทึกแล้ว
statistics-stats = สถิติ
statistics-title = สถิติ

## These strings are no longer used - you do not need to translate them if they
## are not already translated.

