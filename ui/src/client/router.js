import React from 'react';
import PropTypes from 'prop-types';
import {
  BrowserRouter as Router,
  Route,
  // Link
} from 'react-router-dom';
import OdinsonUI from './odinson-ui';

// const config = require('../../config');

const MenuText = ({ name }) => (
  <h4 style={{ textAlign: 'center' }}>{`${name}`}</h4>
);

MenuText.propTypes = {
  name: PropTypes.string.isRequired
};

// Each logical "route" has two components, one for
// the sidebar and one for the main area. We want to
// render both of them in different places when the
// path matches the current URL.
const routes = [
  {
    path: '/',
    exact: true,
    menu: () => <MenuText name="Home" />,
    main: () => <OdinsonUI />
  }
];

function OdinsonRoutes() {
  return (
    <Router>
      <div style={{ display: 'flex' }}>
        {routes.map((route, index) => (
          // You can render a <Route> in as many places
          // as you want in your app. It will render along
          // with any other <Route>s that also match the URL.
          // So, a sidebar or breadcrumbs or anything else
          // that requires you to render multiple things
          // in multiple places at the same URL is nothing
          // more than multiple <Route>s.
          <Route
            key={route}
            path={route.path}
            exact={route.exact}
            component={route.main}
          />
        ))}
      </div>
    </Router>
  );
}

export default OdinsonRoutes;
