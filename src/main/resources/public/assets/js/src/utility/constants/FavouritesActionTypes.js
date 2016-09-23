var mirrorToPath = require('../helpers/path-mirror.js');

var types = mirrorToPath({
  FAVOURITES_FAVOURITES_REQUEST : null,
  FAVOURITES_FAVOURITES_RESPONSE : null,
  FAVOURITES_OPEN_SELECTED : null,
  FAVOURITES_CLOSE_SELECTED : null,
  FAVOURITES_SET_ACTIVE_FAVOURITE : null,
  FAVOURITES_REQUEST_QUERIES : null,
  FAVOURITES_RECEIVE_QUERIES : null,
  FAVOURITES_ADD_QUERY_REQUEST : null,
  FAVOURITES_ADD_QUERY_RESPONSE : null, 
  FAVOURITES_DELETE_QUERY_REQUEST : null, 
  FAVOURITES_DELETE_QUERY_RESPONSE : null
});

module.exports = types;
