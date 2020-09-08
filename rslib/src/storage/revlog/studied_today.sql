select count(),
    coalesce(sum(time) / 1000.0, 0.0)
from revlog
where id > ?
    and type != ?