/*

Title:		jQMeter: a jQuery Progress Meter Plugin
Author:		Gerardo Larios
Version:	0.1.2
Website:	http://www.gerardolarios.com/plugins-and-tools/jqmeter
License: 	Dual licensed under the MIT and GPL licenses.

*/

(function($) {

	//Extend the jQuery prototype
    $.fn.extend({
        jQMeter: function(options) {
            if (options && typeof(options) == 'object') {
                options = $.extend( {}, $.jQMeter.defaults, options );
            }
            this.each(function() {
                new $.jQMeter(this, options);
            });
            return;
        }
    });

	$.jQMeter = function(elem, options) {
		//Define plugin options
		goal = parseInt((options.goal).replace(/\D/g,''));
		raised = parseInt((options.raised).replace(/\D/g,''));
		width = options.width;
		height = options.height;
		bgColor = options.bgColor;
		barColor = options.barColor;
		altBarColor = options.altBarColor;
		meterOrientation = options.meterOrientation;
		animationSpeed = options.animationSpeed;
		counterSpeed = options.counterSpeed;
		displayTotal = options.displayTotal;
		total = (raised / goal) * 100;

		/*
		 * Since the thermometer width/height is set based off of
		 * the total, we force the total to 100% if the goal has
		 * been exceeded.
		 */
		if(total >= 100) {
			total = 100;
		}

		if (altBarColor) {
			$(elem).addClass("moving")
		} else {
			$(elem).removeClass("moving")
		}

		//Create the thermometer layout based on orientation option
		if(meterOrientation == 'vertical') {

			$(elem).html('<div class="therm outer-therm vertical"><div class="therm inner-therm vertical"><span style="display:none;">' + total + '</span></div></div>');
			$(elem).children('.outer-therm').attr('style','width:' + width + ';height:' + height + ';background-color:' + bgColor);
			$(elem).children('.outer-therm').children('.inner-therm').attr('style','background-color:' + barColor + ';height:0;width:100%');
			$(elem).children('.outer-therm').children('.inner-therm').animate({height : total + '%'},animationSpeed);

		} else {

			$(elem).html('<div class="therm outer-therm"><div class="therm inner-therm"><span style="display:none;">' + total + '</span></div></div>');
			$(elem).children('.outer-therm').attr('style','width:' + width + ';height:' + height + ';background-color:' + bgColor);
			$(elem).children('.outer-therm').children('.inner-therm').attr('style','background-color:' + barColor + ';height:' + height + ';width:0');
			$(elem).children('.outer-therm').children('.inner-therm').animate({width : total + '%'},animationSpeed);

		}

		//If the user wants the total percentage to be displayed in the thermometer
		if(displayTotal) {

			//Accomodate the padding of the thermometer to include the total percentage text
			var formatted_height = parseInt(height);
			var padding = (formatted_height/2) - 13 + 'px 10px';

			if(meterOrientation != 'horizontal'){
			  padding = '10px 0';
			}

			$(elem).children('.outer-therm').children('.inner-therm').children().show();
			$(elem).children('.outer-therm').children('.inner-therm').children().css('padding', padding);

			//Animate the percentage total. Borrowed from: http://stackoverflow.com/questions/23006516/jquery-animated-number-counter-from-zero-to-value
			$({ Counter: 0 }).animate({ Counter: $(elem).children('.outer-therm').children('.inner-therm').children().text() }, {
  				duration : counterSpeed,
  				easing : 'swing',
  				step : function() {
   					$(elem).children('.outer-therm').children('.inner-therm').children().text(Math.ceil(this.Counter) + '%');
  				}
			});
		}

		//Add CSS
		$(elem).append(`<style>
			.therm{height:30px;border-radius:5px;}
			.outer-therm{margin:20px 0;}
			.inner-therm span {color: #fff;display: inline-block;float: right; font-size: 20px;font-weight: bold;}
			.vertical.inner-therm span{width:100%;text-align:center;}
			.vertical.outer-therm{position:relative;}
			.vertical.inner-therm{position:absolute;bottom:0;}
			.moving .inner-therm {
			  background-image: repeating-linear-gradient(-45deg, ${barColor}, ${barColor} 12.5px, ${altBarColor} 12.5px, ${altBarColor} 25px) !important;
			  -webkit-animation:progress 2s linear infinite !important;
			  -moz-animation:progress 2s linear infinite !important;
			  -ms-animation:progress 2s linear infinite !important;
			  animation:progress 2s linear infinite !important;
			  background-size: 150% 100% !important;
			}
			@-webkit-keyframes progress{
			  0% {
				background-position: 0 0;
			  }
			  100% {
				background-position: -75px 0px;
			  }
			}
			@-moz-keyframes progress{
			  0% {
				background-position: 0 0;
			  }
			  100% {
				background-position: -75px 0px;
			  }
			}
			@-ms-keyframes progress{
			  0% {
				background-position: 0 0;
			  }
			  100% {
				background-position: -75px 0px;
			  }
			}
			@keyframes progress{
			  0% {
				background-position: 0 0;
			  }
			  100% {
				background-position: -70px 0px;
			  }
			}
		</style>`);


	};

	//Set plugin defaults
	$.jQMeter.defaults = {

		width : '100%',
		height : '50px',
		bgColor : '#444',
		barColor : '#bfd255',
		altBarColor: null,
		meterOrientation : 'horizontal',
		animationSpeed : 2000,
		counterSpeed : 2000,
		displayTotal : true

	};

})(jQuery);