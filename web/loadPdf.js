//
// If absolute URL from the remote server is provided, configure the CORS
// header on that server.
//
//var url = 'http://localhost:8000/edexcel.pdf';
//
// The workerSrc property shall be specified.
//
function loadPdf(url){
  pdfjsLib.GlobalWorkerOptions.workerSrc =
    '_anki/pdf.worker.js';
  //
  // Asynchronous download PDF
  //
  var loadingTask = pdfjsLib.getDocument(url);
  loadingTask.promise.then(function(pdf) {
    //
    // Fetch the first page
    //
    pdf.getPage(1).then(function(page) {
      var scale = 1.5;
      var viewport = page.getViewport({ scale: scale, });
      //
      // Prepare canvas using PDF page dimensions
      //
      var canvas = document.getElementById('the-canvas');
      var context = canvas.getContext('2d');
      canvas.height = viewport.height;
      canvas.width = viewport.width;
      //
      // Render PDF page into canvas context
      //
      var renderContext = {
        canvasContext: context,
        viewport: viewport,
      };
      page.render(renderContext);
    });
  });
};
