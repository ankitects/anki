database-check-corrupt = ସଂଗ୍ରହ ଫାଇଲ୍ ତ୍ରୁଟିଗ୍ରସ୍ତ। ଦୟାକରି ଏକ ସ୍ୱୟଂଚାଳିତ ବ୍ୟାକଅପ୍ ରୁ ପୁନରୁଦ୍ଧାର କରନ୍ତୁ।
database-check-rebuilt = ଡାଟାବେସ୍ ପୁନଃନିର୍ମାଣ ଏବଂ ଅପ୍ଟିମାଇଜ୍ ହୋଇଛି।
database-check-card-properties =
    { $count ->
        [one] { $count }ଟିଏ ଅବୈଧ ପତ୍ର ଗୁଣଧର୍ମ ସ୍ଥିର ହୋଇଛି।
       *[other] { $count }ଟି ଅବୈଧ ପତ୍ର ଗୁଣଧର୍ମ ସ୍ଥିର ହୋଇଛି।
    }
database-check-missing-templates =
    { $count ->
        [one] ନିଖୋଜ ଟେମ୍ପଲେଟ୍ ସହିତ { $count }ଟିଏ ପତ୍ର ବିଲୋପ କରାଯାଇଛି।
       *[other] ନିଖୋଜ ଟେମ୍ପଲେଟ୍ ସହିତ { $count }ଟି ପତ୍ର ବିଲୋପ କରାଯାଇଛି।
    }
database-check-field-count =
    { $count ->
        [one] ଭୁଲ୍ କ୍ଷେତ୍ର ଗଣନା ସହିତ { $count }ଟିଏ ନୋଟ୍ ସ୍ଥିର ହୋଇଛି।
       *[other] ଭୁଲ୍ କ୍ଷେତ୍ର ଗଣନା ସହିତ { $count }ଟି ନୋଟ୍ ସ୍ଥିର ହୋଇଛି।
    }
database-check-card-missing-note =
    { $count ->
        [one] ନିଖୋଜ ନୋଟ୍ ସହିତ { $count }ଟିଏ ପତ୍ର ବିଲୋପ କରାଯାଇଛିି।
       *[other] ନିଖୋଜ ନୋଟ୍ ସହିତ { $count }ଟି ପତ୍ର ବିଲୋପ କରାଯାଇଛିି।
    }
database-check-duplicate-card-ords =
    { $count ->
        [one] ନକଲି ଟେମ୍ପଲେଟ୍ ସହ { $count }ଟିଏ ପତ୍ର ବିଲୋପ ହୋଇଛି।
       *[other] ନକଲି ଟେମ୍ପଲେଟ୍ ସହ { $count }ଟି ପତ୍ର ବିଲୋପ ହୋଇଛି।
    }
database-check-missing-decks =
    { $count ->
        [one] { $count }ଟିଏ ନିଖୋଜ ଡେକ୍ ସ୍ଥିର ହୋଇଛି।
       *[other] { $count }ଟି ନିଖୋଜ ଡେକ୍ ସ୍ଥିର ହୋଇଛି।
    }
database-check-revlog-properties =
    { $count ->
        [one] ଅବୈଧ ଗୁଣଧର୍ମ ସହ { $count }ଟିଏ ସମୀକ୍ଷା ଏଣ୍ଟ୍ରି ସ୍ଥିର ହୋଇଛି।
       *[other] ଅବୈଧ ଗୁଣଧର୍ମ ସହ { $count }ଟି ସମୀକ୍ଷା ଏଣ୍ଟ୍ରି ସ୍ଥିର ହୋଇଛି।
    }
database-check-notes-with-invalid-utf8 =
    { $count ->
        [one] ଅବୈଧ UTF-8 ବର୍ଣ୍ଣ ସହିତ { $count }ଟିଏ ନୋଟ୍ ସ୍ଥିର ହୋଇଛି।
       *[other] ଅବୈଧ UTF-8 ବର୍ଣ୍ଣ ସହିତ { $count }ଟି ନୋଟ୍ ସ୍ଥିର ହୋଇଛି।
    }

## Progress info

database-check-checking-integrity = ସଂଗ୍ରହ ଯାଞ୍ଚ କରୁଛି...
database-check-rebuilding = ପୁନଃନିର୍ମାଣ ହେଉଛି...
database-check-checking-cards = ପତ୍ରଗୁଡ଼ିକ ଯାଞ୍ଚ କରୁଛି...
database-check-checking-notes = ନୋଟ୍ ଗୁଡ଼ିକ ଯାଞ୍ଚ କରୁଛି...
database-check-checking-history = ଇତିବୃତ୍ତି ଯାଞ୍ଚ କରୁଅଛି...
database-check-title = ଡାଟାବେସ୍ ଯାଞ୍ଚ
