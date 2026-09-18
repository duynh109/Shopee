import { ToastContainer } from 'react-toastify'
import 'react-toastify/dist/ReactToastify.css'
import useRouteElements from './useRouteElements'
import ScrollToTop from './ScrollToTop'

function App() {
  const routeElements = useRouteElements()
  return (
    <div>
      <ScrollToTop />
      {routeElements}
      <ToastContainer></ToastContainer>
    </div>
  )
}

export default App
