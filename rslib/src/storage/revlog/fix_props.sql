update revlog
set ivl = min(max(round(ivl), -2147483648), 2147483647),
  lastIvl = min(max(round(lastIvl), -2147483648), 2147483647),
  time = min(max(round(time), 0), 2147483647)
where ivl != min(max(round(ivl), -2147483648), 2147483647)
  or lastIvl != min(max(round(lastIvl), -2147483648), 2147483647)
  or time != min(max(round(time), 0), 2147483647)