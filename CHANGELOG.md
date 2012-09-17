# 0.2.0

* Bug fixes, yada yada.
* Changed format of attribute errors in validator. You now return
  {"the-attr" The error"}, or {"the-attr" ["The", "Errors"]}, instead of
  ["the-attr" "The error"]. This allows for great flexibility. And you can still
  inline your heart of, as {my-ref "my err"} is valid syntax for creating maps.
