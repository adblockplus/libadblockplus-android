{
  {{DEBUG}} console.log('started hiding elements');
  var selectors = JSON.parse({{BRIDGE}}.getElemhideSelectors());
  {{DEBUG}} console.log('parsed selectors: ' + selectors.length);
  for (var i = 0; i < selectors.length; i++)
  {
    var selector = selectors[i];
    if (selector[0] == '#')
    {
      var element = document.getElementById(selector.substr(1));
      if (element != undefined)
      {
        {{DEBUG}} console.log('elem blocked ' + selector);
        element.style.display = 'none';
      };
    }
    else
    {
      var elements = document.getElementsByClassName(selector.substr(1));
      for (var k=0; k < elements.length; k++)
      {
         {{DEBUG}} console.log('elem hidden ' + selector);
         elements[k].style.display = 'none';
      }
    };
  };
  {{DEBUG}} console.log('finished hiding elements');
};