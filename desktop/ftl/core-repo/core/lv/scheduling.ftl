## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.


## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds =
    { $amount ->
        [zero] { $amount } sekunžu
        [one] { $amount } sekunde
       *[other] { $amount } sekundes
    }
scheduling-time-span-minutes =
    { $amount ->
        [zero] { $amount } minūšu
        [one] { $amount } minūte
       *[other] { $amount } minūtes
    }
scheduling-time-span-hours =
    { $amount ->
        [zero] { $amount } stundu
        [one] { $amount } stunda
       *[other] { $amount } stundas
    }
scheduling-time-span-days =
    { $amount ->
        [zero] { $amount } dienu
        [one] { $amount } diena
       *[other] { $amount } dienas
    }
scheduling-time-span-months =
    { $amount ->
        [zero] { $amount } mēnešu
        [one] { $amount } mēnesis
       *[other] { $amount } mēneši
    }
scheduling-time-span-years =
    { $amount ->
        [zero] { $amount } gadu
        [one] { $amount } gads
       *[other] { $amount } gadi
    }

## Shown in the "Congratulations!" message after study finishes.


## Scheduler upgrade

scheduling-update-required =
    Krājumu ir nepieciešams jaunināt V2 plānotājam.
    Lūgums pirms turpināšanas atlasīt { scheduling-update-more-info-button }.

## Other scheduling strings

scheduling-lapses = Misēkļi
scheduling-lapses2 = misēkļi
scheduling-leech-action = Izsūcošo kartīšu darbība
scheduling-leech-threshold = Izsūcošo kartīšu slieksnis
scheduling-reset-counts = Atiestatīt atkārtojumu un misēkļu skaitu
