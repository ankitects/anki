SELECT id,
  guid,
  mid,
  mod,
  usn,
  tags,
  flds,
  cast(sfld AS text),
  csum
FROM notes