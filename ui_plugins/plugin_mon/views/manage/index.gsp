<h3>UI Plugin Management</h3>
<table>
<thead>
  <tr>
    <th>${l.Name}</th>
    <th>${l.Version}</th>
    <th>${l.Description}</th>
  </tr>
</thead>
<tbody>
<% for (p in plugins) { %>
  <tr>
    <td><%= link_to p.name, [action:'showPlugin', id:p] %></td>    
    <td><%= h p.pluginVersion %></td>    
    <td><%= h p.description %></td>
    <td><%= button_to 'Delete', [action:'deletePlugin', id:p,
                                 confirm:'Are you sure?'] %></td>
  </tr>
<% } %>
</tbody>
</table>
