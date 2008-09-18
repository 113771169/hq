<script type="text/javascript">
getDojo();
function selectAlertType(t) {
  if (t == "1") {
    hyperic.html.show('alertsTable');
    hyperic.html.hide('groupAlertsTable');
  } else if (t == "2") {
    hyperic.html.hide('alertsTable');
    hyperic.html.show('groupAlertsTable');
  }
}

function refreshAlertTables() {
  Alerts_refreshTable();
  GroupAlerts_refreshTable();
}
  
function refreshDefTables() {
  Defs_refreshTable();
  TypeDefs_refreshTable();
  GalertDefs_refreshTable();
}

function selectDefType(t) {
  if (t == '1') {
    hyperic.html.show('defsTable')
    hyperic.html.show('excludeTypeBasedInput')
    <% if (superUser) { %> hyperic.html.hide('typeDefsTable') <% } %>
    hyperic.html.hide('galertDefsTable')
  } else if (t == '2') {
    hyperic.html.hide('defsTable')
    hyperic.html.hide('excludeTypeBasedInput')
    <% if (superUser) { %> hyperic.html.show('typeDefsTable') <% } %>
    hyperic.html.hide('galertDefsTable')
  } else if (t == '3') {
    hyperic.html.hide('defsTable')
    hyperic.html.hide('excludeTypeBasedInput')
    <% if (superUser) { %> hyperic.html.hide('typeDefsTable') <% } %>
    hyperic.html.show('galertDefsTable')
  }
}
 
function setSelectedOption() {
  <% if (!isEE) { %>
    selectAlertType('1');
    selectDefType('1');
    return;
  <% } else { %>
    var selectDrop = document.getElementById('alertSelect')
    selectAlertType(selectDrop.options[selectDrop.selectedIndex].value);
    selectDrop = document.getElementById('defSelect')
    selectDefType(selectDrop.options[selectDrop.selectedIndex].value);
  <% } %>
}

dojo.addOnLoad( function(){
    setSelectedOption()
});
</script>

<div dojoType="TabContainer" id="mainTabContainer" 
     style="width: 100%; height:500px; position: relative; z-index: 1;">
  <div dojoType="ContentPane" label="Alerts">
    <div style="margin-top:10px;margin-left:10px;margin-bottom:5px;padding-right:10px;">
      <div style="float:left;width:18%;margin-right:10px;">
        <div class="filters">
          <div class="BlockTitle">${l.AlertFilter}</div>
          <div class="filterBox">
            <div class="fieldSetStacked" style="margin-bottom:8px;">
              <span>${l.Show}:</span>
              <div>
                <input type="radio" id="notFixed" name="show" value="notfixed" onclick="refreshAlertTables()"><label for="notFixed">${l.NotFixed}</label><br>
                <input type="radio" id="escOnly" name="show" value="inescalation" onclick="refreshAlertTables()"><label for="escOnly">${l.InEscalation}</label><br>
                <input type="radio" id="all" name="show" value="all" checked="checked" onclick="refreshAlertTables()"><label for="all">${l.All}</label>
              </div>          
            </div>

            <% if (isEE) { %>
            <div class="fieldSetStacked" style="margin-bottom:8px;">
              <span><label for="alertSelect">${l.AlertType}</label>:</span>
              <div><select id="alertSelect" name="alertSelect" 
                      onchange='selectAlertType(options[selectedIndex].value)'>
                <option value='1'>${l.ClassicAlertsSelect}</option>
                <option value='2'>${l.GroupAlertsSelect}</option>
              </select>
              </div>          
            </div>
            <% } %>
          
            <div class="fieldSetStacked" style="margin-bottom:8px;">
              <span><label for="alertSevSelect">${l.MinPriority}</label>:</span>
              <div><%= selectList(severities, 
                             [id:'alertSevSelect',
                              name:'alertSevSelect',
                              onchange:'refreshAlertTables();'], null) %>
              </div>
            </div>          

            <div class="fieldSetStacked" style="margin-bottom:8px;">
              <span><label for="alertTimeSelect">${l.InTheLast}</label>:</span>
              <div><%= selectList(lastDays, 
                             [id:'alertTimeSelect',
                              name:'alertTimeSelect',
                              onchange:'refreshAlertTables();'], null) %>
                              
              </div>
            </div>
            <div class="fieldSetStacked" style="margin-bottom:8px;">
              <span><label for="alertGroupSelect">${l.GroupFilter}</label>:</span>
              <div><%= selectList(groups, 
                             [id:'alertGroupSelect',
                              name:'alertGroupSelect',
                              onchange:'refreshAlertTables();']) %>
                              
              </div>
            </div>
            </div>
        </div>
      </div>
      <div style="float:right;width:78%;display:inline;height: 445px;overflow-x: hidden; overflow-y: auto;" id="alertsCont">
        <div id="alertsTable" style="display:none;">
          <%= dojoTable(id:'Alerts', title:l.ClassicAlertsTable,
                        refresh:60, url:urlFor(action:'data'),
                        schema:alertSchema, numRows:15) %>
        </div>
        <div id="groupAlertsTable" style="display:none;">
          <%= dojoTable(id:'GroupAlerts', title:l.GroupAlertsTable,
                        refresh:60, url:urlFor(action:'groupData'),
                        schema:galertSchema, numRows:15) %>
        </div>
      </div>
    </div>
<div style="clear:both;height:1px;"></div>
  </div>
  
  <div dojoType="ContentPane" label="Definitions">
   <div style="margin-top:10px;margin-left:10px;margin-bottom:5px;padding-right:10px;">
      <div style="float:left;display:inline;width:18%;margin-right:10px;">
        <div class="filters">
          <div class="BlockTitle">${l.DefFilter}</div>
          <div class="filterBox">
            <% if (isEE) { %>
            <div class="fieldSetStacked" style="margin-bottom:8px;">
              <span><label for="defSelect">${l.DefType}</label>:</span>
              <div><select id="defSelect" name="defSelect"
                      onchange='selectDefType(options[selectedIndex].value)'>
                <option value='1'>${l.ClassicDefsSelect}</option>
                <option value='3'>${l.GroupDefsSelect}</option>
                <% if (superUser) { %>
                  <option value='2'>${l.TypeBasedDefsSelect}</option>
                <% } %>
              </select>
              </div>          
            </div>
            
            <div id="excludeTypeBasedInput" class="fieldSetStacked" 
                 style="margin-bottom:8px;">
              <input id="excludeTypeBased" type="checkbox" name="excludeTypeBased" 
                     value="true"  onchange="Defs_refreshTable();"/>
              <label for="excludeTypeBased">${l.ExcludeTypeBased}</label>
            </div>
            <% } %>

            <div id="onlyShowDisabledInput" class="fieldSetStacked" 
                 style="margin-bottom:8px;">
              <input id="onlyShowDisabled" type="checkbox" name="onlyShowDisabled" 
                     value="true"  onchange="refreshDefTables();"/>
              <label for="onlyShowDisabled">${l.OnlyShowDisabled}</label>
            </div>
            
          </div>
        </div>
      </div>
       <div style="float:right;display:inline;width:78%;height: 445px;overflow-x: hidden; overflow-y: auto;" id="defsCont">
        <div id="defsTable" style="display:none;">
          <%= dojoTable(id:'Defs', title:l.ClassicDefsTable,
                        url:urlFor(action:'defData'),
                        schema:defSchema, numRows:15) %>
        </div>
      
        <div id="typeDefsTable" style="display:none;">
          <% if (superUser) { %>
            <%= dojoTable(id:'TypeDefs', title:l.TypeDefsTable,
                          url:urlFor(action:'typeDefData'),
                          schema:typeDefSchema, numRows:15) %>
          <% } %>
        </div>    

        <div id="galertDefsTable" style="display:none;">
          <%= dojoTable(id:'GalertDefs', 
                        title:l.GroupDefsTable,
                        url:urlFor(action:'galertDefData'),
                        schema:galertDefSchema, 
                        numRows:15,
                        readOnly:true) %>
        </div>    
      </div>
    </div>
 <div style="clear:both;height:1px;"></div>
  </div>
</div>

<script type="text/javascript">
    function getAlertsUrlMap(id) {
        var res = {};
        var sevSelect  = dojo.byId('alertSevSelect');
        var timeSelect = dojo.byId('alertTimeSelect');
        var groupSelect = dojo.byId('alertGroupSelect');
        res['minPriority'] = sevSelect.options[sevSelect.selectedIndex].value;
        res['alertTime']   = timeSelect.options[timeSelect.selectedIndex].value;
        res['group']   = groupSelect.options[groupSelect.selectedIndex].value;

        var escOnly    = dojo.byId('escOnly');
        var notFixed   = dojo.byId('notFixed');
        if (escOnly.checked) {
          res['show'] = escOnly.value;
        }
        else if (notFixed.checked) {
          res['show'] = notFixed.value;
        }
        else {
          res['show'] = 'all';
        }

        return res;
    }
    
    function getDefsUrlMap(id) {
        var res = {};
        <% if (isEE) { %>
          res['excludeTypes']   = dojo.byId('excludeTypeBased').checked;
        <% } %>
        res['onlyShowDisabled'] = dojo.byId('onlyShowDisabled').checked;        
        return res;
    }
    
    Alerts_addUrlXtraCallback(getAlertsUrlMap);
    Defs_addUrlXtraCallback(getDefsUrlMap);

    <% if (isEE) { %>
      GroupAlerts_addUrlXtraCallback(getAlertsUrlMap);
      TypeDefs_addUrlXtraCallback(getDefsUrlMap);
      GalertDefs_addUrlXtraCallback(getDefsUrlMap);
    <% } %>
</script>
