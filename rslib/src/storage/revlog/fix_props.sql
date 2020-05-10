update revlog
set
  ivl = min(max(round(ivl), -2147483648), 2147483647),
  lastIvl = min(max(round(lastIvl), -2147483648), 2147483647)
where
  ivl != min(max(round(ivl), -2147483648), 2147483647)
  or lastIvl != min(max(round(lastIvl), -2147483648), 2147483647)