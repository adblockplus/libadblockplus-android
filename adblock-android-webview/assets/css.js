{
   {{DEBUG}} console.log('starting injecting css rules');
   var selectors = JSON.parse({{BRIDGE}}.getElemhideSelectors());
   {{DEBUG}} console.log('parsed selectors: ' + selectors.length);
   var head = document.getElementsByTagName("head")[0];
   var style = document.createElement("style");
   head.appendChild(style);
   var sheet = style.sheet ? style.sheet : style.styleSheet;
   for (var i=0; i<selectors.length; i++)
   {
     if (sheet.insertRule)
     {
       sheet.insertRule(selectors[i] + ' { display: none !important; }', 0);
     }
     else
     {
       sheet.addRule(selectors[i], 'display: none !important;', 0);
     }
   }
   {{DEBUG}} console.log('finished injecting css rules');
}